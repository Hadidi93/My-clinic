package com.myclinic.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myclinic.app.data.auth.AuthFailure
import com.myclinic.app.data.auth.AuthRepository
import com.myclinic.domain.error.AuthError
import com.myclinic.domain.validation.EmailValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val showEmailError: Boolean = false,
    val showPasswordError: Boolean = false,
    val loading: Boolean = false,
    val error: AuthError? = null,
)

/**
 * Holds what the user typed on the sign-in screen and performs the sign-in.
 * On success nothing needs to happen here: the root screen notices the new
 * session and moves on to the app automatically.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, showEmailError = false, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, showPasswordError = false, error = null) }

    fun signIn() {
        val s = _state.value
        val emailOk = EmailValidator.isValid(s.email)
        val passwordOk = s.password.isNotEmpty()
        if (!emailOk || !passwordOk) {
            _state.update { it.copy(showEmailError = !emailOk, showPasswordError = !passwordOk) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.signIn(s.email, s.password)
            _state.update {
                it.copy(
                    loading = false,
                    // Clear the password from memory once it has been used.
                    password = if (result.isSuccess) "" else it.password,
                    error = result.exceptionOrNull()?.let { e -> (e as? AuthFailure)?.error ?: AuthError.UNKNOWN },
                )
            }
        }
    }
}
