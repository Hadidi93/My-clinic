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

data class SignUpUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val showErrors: Boolean = false,
    val loading: Boolean = false,
    val error: AuthError? = null,
    /** Set when the account was created; the screen then shows "check your email". */
    val registeredEmail: String? = null,
) {
    val nameInvalid get() = fullName.trim().length !in 3..120
    val emailInvalid get() = !EmailValidator.isValid(email)
    val passwordErrors: List<PasswordError> get() = PasswordPolicy.validate(password, confirmPassword)
}

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    fun onFullNameChange(v: String) = _state.update { it.copy(fullName = v, error = null) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onConfirmPasswordChange(v: String) = _state.update { it.copy(confirmPassword = v, error = null) }

    fun signUp(language: String) {
        val s = _state.value
        if (s.nameInvalid || s.emailInvalid || s.passwordErrors.isNotEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.signUp(s.fullName, s.email, s.password, language)
            _state.update {
                if (result.isSuccess) {
                    it.copy(loading = false, password = "", confirmPassword = "", registeredEmail = s.email.trim())
                } else {
                    it.copy(loading = false, error = (result.exceptionOrNull() as? AuthFailure)?.error ?: AuthError.UNKNOWN)
                }
            }
        }
    }

    fun onRegisteredHandled() = _state.update { it.copy(registeredEmail = null) }
}
