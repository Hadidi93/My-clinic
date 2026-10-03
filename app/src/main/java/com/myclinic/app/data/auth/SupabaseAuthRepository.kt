package com.myclinic.app.data.auth

import android.content.Intent
import com.myclinic.domain.error.AuthError
import com.myclinic.domain.error.AuthErrorClassifier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : AuthRepository {

    private val recoveryPending = MutableStateFlow(false)
    override val passwordRecoveryPending: StateFlow<Boolean> = recoveryPending.asStateFlow()

    private val _linkMessage = MutableStateFlow<LinkMessage?>(null)
    override val linkMessage: StateFlow<LinkMessage?> = _linkMessage.asStateFlow()
    override fun clearLinkMessage() {
        _linkMessage.value = null
    }

    /** Lives as long as the app, so a link is handled even if the screen changes meanwhile. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The last signed-in user, so a session reload without user details doesn't look like "loading". */
    @Volatile private var lastSignedIn: AuthState.SignedIn? = null

    override val authState: Flow<AuthState> = supabase.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    // When the app comes back from another app (e.g. the camera), the
                    // session is reloaded from storage or refreshed; it may briefly
                    // arrive without the user's details. Keep showing the same user.
                    val user = status.session.user ?: supabase.auth.currentUserOrNull()
                    if (user != null) AuthState.SignedIn(user.id, user.email) else lastSignedIn ?: AuthState.Loading
                }
                is SessionStatus.NotAuthenticated -> AuthState.SignedOut
                is SessionStatus.Initializing -> AuthState.Loading
                // Token refresh failed (usually no internet). Supabase keeps
                // retrying; keep the user signed in to their cached session.
                is SessionStatus.RefreshFailure ->
                    supabase.auth.currentUserOrNull()?.let { AuthState.SignedIn(it.id, it.email) }
                        ?: AuthState.SignedOut
            }
        }
        .onEach { state ->
            when (state) {
                is AuthState.SignedIn -> lastSignedIn = state
                AuthState.SignedOut -> lastSignedIn = null
                AuthState.Loading -> Unit
            }
        }
        .distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): Result<Unit> = authCall {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signUp(fullName: String, email: String, password: String, language: String): Result<Unit> =
        authCall {
            supabase.auth.signUpWith(Email, redirectUrl = AuthRedirects.VERIFY_EMAIL_URL) {
                this.email = email.trim()
                this.password = password
                // Read by the database trigger handle_new_user() to fill the profile.
                data = buildJsonObject {
                    put("full_name", fullName.trim())
                    put("preferred_language", language)
                }
            }
        }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = authCall {
        supabase.auth.resetPasswordForEmail(email.trim(), redirectUrl = AuthRedirects.RESET_PASSWORD_URL)
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> = authCall {
        supabase.auth.updateUser { password = newPassword }
        recoveryPending.value = false
    }

    override suspend fun signOut() {
        recoveryPending.value = false
        runCatching { supabase.auth.signOut() }
    }

    override fun handleDeepLink(intent: Intent?) {
        if (intent == null) return
        val data = intent.data ?: return
        if (data.scheme != AuthRedirects.SCHEME || data.host != AuthRedirects.HOST) return
        val isReset = data.path == AuthRedirects.RESET_PATH
        val code = data.getQueryParameter("code")
        // Supabase reports a bad link (e.g. expired) in the query or after "#".
        val linkError = data.getQueryParameter("error")
            ?: data.fragment?.split('&')?.firstOrNull { it.startsWith("error=") }

        scope.launch {
            // Wait until the saved login has been loaded. Otherwise that loading
            // step can finish after the link has signed us in and overwrite it
            // with "signed out" (the bug that left users on the sign-in screen).
            supabase.auth.awaitInitialization()
            // Set before signing in, so the app goes straight to "choose a new password".
            if (isReset && code != null) recoveryPending.value = true
            when {
                code != null -> try {
                    supabase.auth.exchangeCodeForSession(code)
                    _linkMessage.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The code was only issued because Supabase accepted the
                    // link, so a verification link did verify the email.
                    recoveryPending.value = false
                    _linkMessage.value = if (isReset) LinkMessage.RESET_LINK_FAILED else LinkMessage.EMAIL_VERIFIED_SIGN_IN
                }
                linkError != null -> {
                    recoveryPending.value = false
                    _linkMessage.value = if (isReset) LinkMessage.RESET_LINK_FAILED else LinkMessage.LINK_EXPIRED
                }
            }
        }
    }

    /** Runs an auth call and converts any failure into an [AuthFailure] with a known reason. */
    private suspend fun authCall(block: suspend () -> Unit): Result<Unit> =
        try {
            block()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestException) {
            Result.failure(AuthFailure(AuthError.NETWORK, e))
        } catch (e: HttpRequestTimeoutException) {
            Result.failure(AuthFailure(AuthError.NETWORK, e))
        } catch (e: IOException) {
            Result.failure(AuthFailure(AuthError.NETWORK, e))
        } catch (e: RestException) {
            Result.failure(AuthFailure(AuthErrorClassifier.classify("${e.error} ${e.description} ${e.message}"), e))
        } catch (e: Exception) {
            Result.failure(AuthFailure(AuthErrorClassifier.classify(e.message), e))
        }
}
