package com.myclinic.app.data.auth

import android.content.Intent
import com.myclinic.domain.error.AuthError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Links in Supabase emails come back into the app through these URLs. */
object AuthRedirects {
    const val SCHEME = "myclinic"
    const val HOST = "auth-callback"
    const val VERIFY_EMAIL_URL = "$SCHEME://$HOST/verified"
    const val RESET_PASSWORD_URL = "$SCHEME://$HOST/reset"
    const val RESET_PATH = "/reset"
}

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String?) : AuthState
}

/** What happened with an email link (verify / reset) that could not sign the user in. */
enum class LinkMessage {
    /** The email was verified, but the automatic sign-in didn't complete. Sign in normally. */
    EMAIL_VERIFIED_SIGN_IN,
    /** The link expired or was already used. */
    LINK_EXPIRED,
    /** A password-reset link didn't work. Request a new one. */
    RESET_LINK_FAILED,
}

/** Thrown (inside Result.failure) when an auth call fails; [error] says why. */
class AuthFailure(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)

/** Sign-up, sign-in, password reset and sign-out. */
interface AuthRepository {
    val authState: Flow<AuthState>

    /** True after the user opened a "reset password" email link, until they set a new password. */
    val passwordRecoveryPending: StateFlow<Boolean>

    /** Set when an email link opened the app but could not sign the user in. */
    val linkMessage: StateFlow<LinkMessage?>
    fun clearLinkMessage()

    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signUp(fullName: String, email: String, password: String, language: String): Result<Unit>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    suspend fun signOut()

    /** Hands email links (verify / reset) to Supabase so it can finish the sign-in. */
    fun handleDeepLink(intent: Intent?)
}
