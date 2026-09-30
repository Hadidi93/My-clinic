package com.myclinic.app.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myclinic.app.R
import com.myclinic.app.ui.components.AppTextField
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.PasswordField
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.components.message

@Composable
fun LoginScreen(
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    AuthScaffold(
        title = stringResource(R.string.app_name),
        subtitle = stringResource(R.string.login_subtitle),
    ) {
        AppTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(R.string.email),
            keyboardType = KeyboardType.Email,
            error = if (state.showEmailError) stringResource(R.string.error_invalid_email) else null,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.password),
            error = if (state.showPasswordError) stringResource(R.string.error_password_required) else null,
            imeAction = ImeAction.Done,
            onImeAction = { focus.clearFocus(); viewModel.signIn() },
        )
        ErrorMessage(state.error?.message())
        PrimaryButton(
            text = stringResource(R.string.login_button),
            onClick = { focus.clearFocus(); viewModel.signIn() },
            loading = state.loading,
        )
        TextButton(onClick = onForgotPassword, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
            Text(stringResource(R.string.forgot_password))
        }
        TextButton(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
            Text(stringResource(R.string.no_account))
        }
    }
}
