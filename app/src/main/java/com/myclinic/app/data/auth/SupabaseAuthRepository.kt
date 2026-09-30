package com.myclinic.app.data.auth

import android.content.Intent
import com.myclinic.domain.error.AuthError
import com.myclinic.domain.error.AuthErrorClassifier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    override val authState: Flow<AuthState> = supabase.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val user = status.session.user
                    if (user != null) AuthState.SignedIn(user.id, user.email) else AuthState.Loading
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
        if (data.path == AuthRedirects.RESET_PATH) recoveryPending.value = true
        supabase.handleDeeplinks(intent)
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
