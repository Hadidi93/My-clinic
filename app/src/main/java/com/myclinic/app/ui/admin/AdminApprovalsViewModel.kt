package com.myclinic.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myclinic.app.data.DataError
import com.myclinic.app.data.doctor.DoctorRepository
import com.myclinic.app.data.toDataError
import com.myclinic.domain.model.Doctor
import com.myclinic.domain.model.VerificationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminApprovalsUiState(
    val loading: Boolean = true,
    val doctors: List<Doctor> = emptyList(),
    val busyDoctorId: String? = null,
    val error: DataError? = null,
    /** Signed link to the licence photo currently open in the viewer. */
    val licenseViewerUrl: String? = null,
    /** Name + decision of the last action, for the snackbar. */
    val lastDecision: Pair<String, VerificationStatus>? = null,
)

@HiltViewModel
class AdminApprovalsViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminApprovalsUiState())
    val state: StateFlow<AdminApprovalsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            doctorRepository.doctorsAwaitingVerification()
                .onSuccess { list -> _state.update { it.copy(loading = false, doctors = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toDataError()) } }
        }
    }

    fun approve(doctor: Doctor) = decide(doctor, VerificationStatus.VERIFIED, note = null)
    fun reject(doctor: Doctor, reason: String) = decide(doctor, VerificationStatus.REJECTED, note = reason)

    private fun decide(doctor: Doctor, status: VerificationStatus, note: String?) {
        _state.update { it.copy(busyDoctorId = doctor.id, error = null) }
        viewModelScope.launch {
            doctorRepository.setVerification(doctor.id, status, note)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            busyDoctorId = null,
                            doctors = s.doctors.filterNot { it.id == doctor.id },
                            lastDecision = doctor.fullName to status,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(busyDoctorId = null, error = e.toDataError()) } }
        }
    }

    fun openLicense(doctor: Doctor) {
        val path = doctor.licenseDocumentPath ?: return
        viewModelScope.launch {
            val url = doctorRepository.licenseDocumentUrl(path)
            _state.update { it.copy(licenseViewerUrl = url, error = if (url == null) DataError.UNKNOWN else null) }
        }
    }

    fun closeLicense() = _state.update { it.copy(licenseViewerUrl = null) }
    fun onDecisionShown() = _state.update { it.copy(lastDecision = null) }
}
