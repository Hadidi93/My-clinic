package com.myclinic.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.myclinic.app.R
import com.myclinic.domain.error.AuthError
import com.myclinic.domain.validation.PasswordError
import com.myclinic.domain.validation.PasswordPolicy
import com.myclinic.domain.validation.ProfileField

// Turns error codes from the domain module into translated text.

@Composable
fun AuthError.message(): String = stringResource(
    when (this) {
        AuthError.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
        AuthError.EMAIL_NOT_CONFIRMED -> R.string.error_email_not_confirmed
        AuthError.EMAIL_ALREADY_REGISTERED -> R.string.error_email_registered
        AuthError.WEAK_PASSWORD -> R.string.error_weak_password
        AuthError.RATE_LIMITED -> R.string.error_rate_limited
        AuthError.NETWORK -> R.string.error_network
        AuthError.UNKNOWN -> R.string.error_unknown
    },
)

/** Shows the first password problem, which is the one to fix next. */
@Composable
fun List<PasswordError>.firstMessage(): String? = firstOrNull()?.let {
    when (it) {
        PasswordError.TOO_SHORT -> stringResource(R.string.error_password_too_short, PasswordPolicy.MIN_LENGTH)
        PasswordError.TOO_LONG -> stringResource(R.string.error_password_too_long)
        PasswordError.NEEDS_LETTER -> stringResource(R.string.error_password_needs_letter)
        PasswordError.NEEDS_DIGIT -> stringResource(R.string.error_password_needs_digit)
        PasswordError.MISMATCH -> stringResource(R.string.error_password_mismatch)
    }
}

@Composable
fun ProfileField.errorMessage(): String = stringResource(
    when (this) {
        ProfileField.FULL_NAME -> R.string.error_full_name
        ProfileField.SPECIALTY -> R.string.error_specialty
        ProfileField.HOSPITAL -> R.string.error_hospital
        ProfileField.LICENSE_NUMBER -> R.string.error_license
        ProfileField.PHONE -> R.string.error_phone
    },
)
