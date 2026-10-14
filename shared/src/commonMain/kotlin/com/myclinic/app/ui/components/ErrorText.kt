package com.myclinic.app.ui.components

import com.myclinic.shared.resources.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.myclinic.domain.error.AuthError
import com.myclinic.domain.validation.PasswordError
import com.myclinic.domain.validation.PasswordPolicy
import com.myclinic.domain.validation.ProfileField

// Turns error codes from the domain module into translated text.

@Composable
fun AuthError.message(): String = stringResource(
    when (this) {
        AuthError.INVALID_CREDENTIALS -> Res.string.error_invalid_credentials
        AuthError.EMAIL_NOT_CONFIRMED -> Res.string.error_email_not_confirmed
        AuthError.EMAIL_ALREADY_REGISTERED -> Res.string.error_email_registered
        AuthError.WEAK_PASSWORD -> Res.string.error_weak_password
        AuthError.RATE_LIMITED -> Res.string.error_rate_limited
        AuthError.NETWORK -> Res.string.error_network
        AuthError.UNKNOWN -> Res.string.error_unknown
    },
)

/** Shows the first password problem, which is the one to fix next. */
@Composable
fun List<PasswordError>.firstMessage(): String? = firstOrNull()?.let {
    when (it) {
        PasswordError.TOO_SHORT -> stringResource(Res.string.error_password_too_short, PasswordPolicy.MIN_LENGTH)
        PasswordError.TOO_LONG -> stringResource(Res.string.error_password_too_long)
        PasswordError.NEEDS_LETTER -> stringResource(Res.string.error_password_needs_letter)
        PasswordError.NEEDS_DIGIT -> stringResource(Res.string.error_password_needs_digit)
        PasswordError.MISMATCH -> stringResource(Res.string.error_password_mismatch)
    }
}

@Composable
fun ProfileField.errorMessage(): String = stringResource(
    when (this) {
        ProfileField.FULL_NAME -> Res.string.error_full_name
        ProfileField.SPECIALTY -> Res.string.error_specialty
        ProfileField.HOSPITAL -> Res.string.error_hospital
        ProfileField.LICENSE_NUMBER -> Res.string.error_license
        ProfileField.PHONE -> Res.string.error_phone
    },
)
