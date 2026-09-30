package com.myclinic.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myclinic.app.data.auth.AuthFailure
import com.myclinic.app.data.auth.AuthRepository
import com.myclinic.domain.error.AuthError
import com.myclinic.domain.validation.EmailValidator
import com.myclinic.domain.validation.PasswordError
import com.myclinic.domain.validation.PasswordPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotPasswordUiState(
    val email: String = "",
    val showEmailError: Boolean = false,
    val loading: Boolean = false,
    val sentTo: String? = null,
    val error: AuthError? = null,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, showEmailError = false, error = null) }

    fun send() {
        val email = _state.value.email
        if (!EmailValidator.isValid(email)) {
            _state.update { it.copy(showEmailError = true) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(email)
            _state.update {
                // For privacy we show the same message whether or not the
                // email has an account (prevents checking who is registered).
                val error = (result.exceptionOrNull() as? AuthFailure)?.error
                if (error == AuthError.NETWORK || error == AuthError.RATE_LIMITED) {
                    it.copy(loading = false, error = error)
                } else {
                    it.copy(loading = false, sentTo = email.trim())
                }
            }
        }
    }
}

data class ResetPasswordUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val showErrors: Boolean = false,
    val loading: Boolean = false,
    val error: AuthError? = null,
) {
    val passwordErrors: List<PasswordError> get() = PasswordPolicy.validate(password, confirmPassword)
}

/** Shown after the user opens the reset link from their email. */
@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ResetPasswordUiState())
    val state: StateFlow<ResetPasswordUiState> = _state.asStateFlow()

    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onConfirmPasswordChange(v: String) = _state.update { it.copy(confirmPassword = v, error = null) }

    fun submit() {
        val s = _state.value
        if (s.passwordErrors.isNotEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.updatePassword(s.password)
            _state.update {
                it.copy(
                    loading = false,
                    password = if (result.isSuccess) "" else it.password,
                    confirmPassword = if (result.isSuccess) "" else it.confirmPassword,
                    error = (result.exceptionOrNull() as? AuthFailure)?.error
                        ?: result.exceptionOrNull()?.let { AuthError.UNKNOWN },
                )
            }
        }
    }

    fun cancel() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
