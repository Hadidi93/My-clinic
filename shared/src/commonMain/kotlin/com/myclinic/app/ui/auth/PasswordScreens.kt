package com.myclinic.app.ui.auth

import com.myclinic.shared.resources.*
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myclinic.app.ui.components.AppTextField
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.MessageCard
import com.myclinic.app.ui.components.PasswordField
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.SecondaryButton
import com.myclinic.app.ui.components.firstMessage
import com.myclinic.app.ui.components.message
import com.myclinic.domain.validation.PasswordError
import com.myclinic.domain.validation.PasswordPolicy

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, viewModel: ForgotPasswordViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    AuthScaffold(
        title = stringResource(Res.string.forgot_title),
        subtitle = stringResource(Res.string.forgot_body),
        onBack = onBack,
    ) {
        val sentTo = state.sentTo
        if (sentTo != null) {
            MessageCard(
                title = stringResource(Res.string.reset_link_sent, sentTo),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            PrimaryButton(stringResource(Res.string.back_to_sign_in), onBack)
        } else {
            AppTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(Res.string.email),
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Send,
                onImeAction = { focus.clearFocus(); viewModel.send() },
                error = if (state.showEmailError) stringResource(Res.string.error_invalid_email) else null,
            )
            ErrorMessage(state.error?.message())
            PrimaryButton(
                text = stringResource(Res.string.send_reset_link),
                onClick = { focus.clearFocus(); viewModel.send() },
                loading = state.loading,
            )
        }
    }
}

/** Shown when the app was opened from a password-reset email link. */
@Composable
fun ResetPasswordScreen(viewModel: ResetPasswordViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val errors = state.passwordErrors

    AuthScaffold(title = stringResource(Res.string.new_password_title)) {
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(Res.string.new_password),
            imeAction = ImeAction.Next,
            supportingText = stringResource(Res.string.password_rules, PasswordPolicy.MIN_LENGTH),
            error = if (state.showErrors) errors.filter { it != PasswordError.MISMATCH }.firstMessage() else null,
        )
        PasswordField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = stringResource(Res.string.confirm_password),
            error = if (state.showErrors && PasswordError.MISMATCH in errors) {
                stringResource(Res.string.error_password_mismatch)
            } else {
                null
            },
            onImeAction = { focus.clearFocus(); viewModel.submit() },
        )
        ErrorMessage(state.error?.message())
        PrimaryButton(
            text = stringResource(Res.string.update_password),
            onClick = { focus.clearFocus(); viewModel.submit() },
            loading = state.loading,
        )
        SecondaryButton(text = stringResource(Res.string.cancel), onClick = viewModel::cancel)
    }
}
