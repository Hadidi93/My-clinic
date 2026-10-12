package com.myclinic.domain.error

/** Errors the sign-in / sign-up screens know how to explain to the user. */
enum class AuthError {
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    EMAIL_ALREADY_REGISTERED,
    WEAK_PASSWORD,
    RATE_LIMITED,
    NETWORK,
    UNKNOWN,
}

/**
 * Turns the text of a Supabase Auth error into an [AuthError]. Supabase
 * returns short codes such as "invalid_credentials" plus an English
 * message; we match on both so the app keeps working if either changes.
 */
object AuthErrorClassifier {
    fun classify(errorText: String?): AuthError {
        val text = errorText.orEmpty().lowercase()
        return when {
            "invalid_credentials" in text || "invalid login credentials" in text -> AuthError.INVALID_CREDENTIALS
            "email_not_confirmed" in text || "email not confirmed" in text -> AuthError.EMAIL_NOT_CONFIRMED
            "user_already_exists" in text || "already registered" in text ||
                "email_exists" in text -> AuthError.EMAIL_ALREADY_REGISTERED
            "weak_password" in text || "password should" in text -> AuthError.WEAK_PASSWORD
            "rate_limit" in text || "rate limit" in text || "too many requests" in text -> AuthError.RATE_LIMITED
            else -> AuthError.UNKNOWN
        }
    }
}
