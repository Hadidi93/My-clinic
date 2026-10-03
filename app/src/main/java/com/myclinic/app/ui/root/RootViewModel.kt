package com.myclinic.app.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myclinic.app.data.auth.AuthRepository
import com.myclinic.app.data.auth.AuthState
import com.myclinic.app.data.doctor.DoctorRepository
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.domain.model.Doctor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
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

private val RootState.userId: String?
    get() = when (this) {
        is RootState.Ready -> doctor.id
        is RootState.NeedsProfile -> doctor.id
        else -> null
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
    private val patientRepository: PatientRepository,
) : ViewModel() {

    private val profileLoadFailed = MutableStateFlow(false)

    /**
     * The screens last shown to a signed-in user. If the session reloads for a
     * moment (e.g. coming back from the camera app), we keep showing them
     * instead of a loading screen, which would restart the app at the home page
     * and lose what the user was doing.
     */
    private var lastSignedInState: RootState? = null

    val state: StateFlow<RootState> = combine(
        authRepository.authState,
        authRepository.passwordRecoveryPending,
        doctorRepository.myProfile,
        profileLoadFailed,
    ) { auth, recovery, doctor, failed ->
        when (auth) {
            AuthState.Loading -> lastSignedInState ?: RootState.Loading
            AuthState.SignedOut -> RootState.SignedOut
            is AuthState.SignedIn -> when {
                recovery -> RootState.PasswordRecovery
                doctor != null && doctor.id == auth.userId ->
                    if (doctor.isProfileComplete) RootState.Ready(doctor) else RootState.NeedsProfile(doctor)
                failed -> RootState.ProfileLoadFailed
                // Profile reloading for the same user: keep the current screens.
                else -> lastSignedInState?.takeIf { it.userId == auth.userId } ?: RootState.Loading
            }
        }
    }.onEach { state ->
        when (state) {
            is RootState.Ready, is RootState.NeedsProfile -> lastSignedInState = state
            RootState.SignedOut -> lastSignedInState = null
            else -> Unit
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

    init {
        viewModelScope.launch {
            authRepository.authState.collect { auth ->
                when (auth) {
                    is AuthState.SignedIn -> {
                        loadProfile()
                        patientRepository.startSync()
                    }
                    AuthState.SignedOut -> {
                        doctorRepository.clear()
                        // No patient data stays on the phone after sign-out
                        // (also covers a session that expired on its own).
                        patientRepository.clearLocalData()
                    }
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

    /** Message about an email link that opened the app but couldn't sign in (shown on the sign-in screen). */
    val linkMessage = authRepository.linkMessage
    fun clearLinkMessage() = authRepository.clearLinkMessage()

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
