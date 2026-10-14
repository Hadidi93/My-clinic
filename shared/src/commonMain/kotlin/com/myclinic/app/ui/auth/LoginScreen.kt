package com.myclinic.app.ui.auth

import com.myclinic.shared.resources.*
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myclinic.app.data.auth.LinkMessage
import com.myclinic.app.ui.components.MessageCard
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
    linkMessage: LinkMessage? = null,
    onDismissLinkMessage: () -> Unit = {},
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    AuthScaffold(
        title = stringResource(Res.string.app_name),
        subtitle = stringResource(Res.string.login_subtitle),
    ) {
        linkMessage?.let { message ->
            val verified = message == LinkMessage.EMAIL_VERIFIED_SIGN_IN
            MessageCard(
                title = stringResource(
                    when (message) {
                        LinkMessage.EMAIL_VERIFIED_SIGN_IN -> Res.string.link_email_verified
                        LinkMessage.LINK_EXPIRED -> Res.string.link_expired
                        LinkMessage.RESET_LINK_FAILED -> Res.string.link_reset_failed
                    },
                ),
                container = if (verified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                content = if (verified) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismissLinkMessage) { Text(stringResource(Res.string.close)) }
        }
        AppTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(Res.string.email),
            keyboardType = KeyboardType.Email,
            error = if (state.showEmailError) stringResource(Res.string.error_invalid_email) else null,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(Res.string.password),
            error = if (state.showPasswordError) stringResource(Res.string.error_password_required) else null,
            imeAction = ImeAction.Done,
            onImeAction = { focus.clearFocus(); viewModel.signIn() },
        )
        ErrorMessage(state.error?.message())
        PrimaryButton(
            text = stringResource(Res.string.login_button),
            onClick = { focus.clearFocus(); viewModel.signIn() },
            loading = state.loading,
        )
        TextButton(onClick = onForgotPassword, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
            Text(stringResource(Res.string.forgot_password))
        }
        TextButton(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
            Text(stringResource(Res.string.no_account))
        }
    }
}
