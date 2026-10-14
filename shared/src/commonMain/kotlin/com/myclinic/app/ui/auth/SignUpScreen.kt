package com.myclinic.app.ui.auth

import com.myclinic.shared.resources.*
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myclinic.app.ui.components.AppTextField
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.PasswordField
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.components.currentAppLanguage
import com.myclinic.app.ui.components.firstMessage
import com.myclinic.app.ui.components.message
import com.myclinic.domain.validation.PasswordError
import com.myclinic.domain.validation.PasswordPolicy

@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onRegistered: (email: String) -> Unit,
    viewModel: SignUpViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    LaunchedEffect(state.registeredEmail) {
        state.registeredEmail?.let {
            onRegistered(it)
            viewModel.onRegisteredHandled()
        }
    }

    val passwordErrors = state.passwordErrors
    AuthScaffold(title = stringResource(Res.string.signup_title), onBack = onBack) {
        AppTextField(
            value = state.fullName,
            onValueChange = viewModel::onFullNameChange,
            label = stringResource(Res.string.full_name),
            capitalization = KeyboardCapitalization.Words,
            error = if (state.showErrors && state.nameInvalid) stringResource(Res.string.error_full_name) else null,
        )
        AppTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(Res.string.email),
            keyboardType = KeyboardType.Email,
            error = if (state.showErrors && state.emailInvalid) stringResource(Res.string.error_invalid_email) else null,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(Res.string.password),
            imeAction = ImeAction.Next,
            supportingText = stringResource(Res.string.password_rules, PasswordPolicy.MIN_LENGTH),
            error = if (state.showErrors) passwordErrors.filter { it != PasswordError.MISMATCH }.firstMessage() else null,
        )
        PasswordField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = stringResource(Res.string.confirm_password),
            error = if (state.showErrors && PasswordError.MISMATCH in passwordErrors) {
                stringResource(Res.string.error_password_mismatch)
            } else {
                null
            },
            onImeAction = { focus.clearFocus(); viewModel.signUp(currentAppLanguage()) },
        )
        Text(stringResource(Res.string.signup_verification_note), style = MaterialTheme.typography.bodyMedium)
        ErrorMessage(state.error?.message())
        PrimaryButton(
            text = stringResource(Res.string.signup_button),
            onClick = { focus.clearFocus(); viewModel.signUp(currentAppLanguage()) },
            loading = state.loading,
        )
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
            Text(stringResource(Res.string.have_account))
        }
    }
}

@Composable
fun CheckEmailScreen(email: String, onBackToSignIn: () -> Unit) {
    AuthScaffold(
        title = stringResource(Res.string.check_email_title),
        subtitle = stringResource(Res.string.check_email_body, email),
    ) {
        PrimaryButton(text = stringResource(Res.string.back_to_sign_in), onClick = onBackToSignIn)
    }
}
