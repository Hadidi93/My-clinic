package com.myclinic.app.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myclinic.app.data.auth.AuthRepository
import com.myclinic.app.data.auth.AuthState
import com.myclinic.app.data.doctor.DoctorRepository
import com.myclinic.domain.model.Doctor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which part of the app to show. */
sealed interface RootState {
    data object Loading : RootState
    data object SignedOut : RootState
    data object PasswordRecovery : RootState
    data object ProfileLoadFailed : RootState
    /** Signed in, but required profile fields are missing: show profile setup. */
    data class NeedsProfile(val doctor: Doctor) : RootState
    data class Ready(val doctor: Doctor) : RootState
}

/**
 * Watches the login session and the doctor's profile, and decides what the
 * user should see. Screens never navigate between "signed in" and "signed
 * out" themselves; they change the session and this reacts.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val doctorRepository: DoctorRepository,
) : ViewModel() {

    private val profileLoadFailed = MutableStateFlow(false)

    val state: StateFlow<RootState> = combine(
        authRepository.authState,
        authRepository.passwordRecoveryPending,
        doctorRepository.myProfile,
        profileLoadFailed,
    ) { auth, recovery, doctor, failed ->
        when (auth) {
            AuthState.Loading -> RootState.Loading
            AuthState.SignedOut -> RootState.SignedOut
            is AuthState.SignedIn -> when {
                recovery -> RootState.PasswordRecovery
                doctor != null && doctor.id == auth.userId ->
                    if (doctor.isProfileComplete) RootState.Ready(doctor) else RootState.NeedsProfile(doctor)
                failed -> RootState.ProfileLoadFailed
                else -> RootState.Loading
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

    init {
        viewModelScope.launch {
            authRepository.authState.collect { auth ->
                when (auth) {
                    is AuthState.SignedIn -> loadProfile()
                    AuthState.SignedOut -> doctorRepository.clear()
                    AuthState.Loading -> Unit
                }
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            profileLoadFailed.value = false
            profileLoadFailed.value = doctorRepository.refreshMyProfile().isFailure
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
