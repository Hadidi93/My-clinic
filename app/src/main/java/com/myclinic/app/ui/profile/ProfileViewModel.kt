package com.myclinic.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myclinic.app.data.DataError
import com.myclinic.app.data.doctor.DoctorRepository
import com.myclinic.app.data.media.ImageCompressor
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.app.data.toDataError
import com.myclinic.app.data.facilities.FacilityRepository
import com.myclinic.domain.model.AccountType
import com.myclinic.domain.model.Doctor
import com.myclinic.domain.record.Facility
import com.myclinic.domain.model.VerificationStatus
import com.myclinic.domain.validation.ProfileField
import com.myclinic.domain.validation.ProfileInput
import com.myclinic.domain.validation.ProfileValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val fullName: String = "",
    val specialty: String = "",
    val hospital: String = "",
    val licenseNumber: String = "",
    val phone: String = "",
    val language: String = "en",
    val email: String = "",
    val verificationStatus: VerificationStatus = VerificationStatus.PENDING,
    val verificationNote: String? = null,
    val photoUrl: String? = null,
    val hasLicenseDocument: Boolean = false,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val uploadingPhoto: Boolean = false,
    val uploadingLicense: Boolean = false,
    val error: DataError? = null,
    /** One-off event: show the "Profile saved" snackbar. */
    val savedEvent: Boolean = false,
    /** Number of unsynced changes to warn about before signing out (null = no warning showing). */
    val signOutWarning: Int? = null,
    /** One-off event: go ahead and sign out. */
    val signOutConfirmed: Boolean = false,
    /** Doctor, or lab/radiology staff (who use the department inbox instead of patient records). */
    val accountType: AccountType = AccountType.DOCTOR,
    val facilityId: String? = null,
    val facilities: List<Facility> = emptyList(),
    /** What the account was when the screen opened, to warn that changing it needs re-approval. */
    val savedAccountType: AccountType = AccountType.DOCTOR,
    /** resident / specialist / consultant (doctors only). */
    val grade: String? = null,
    /** What colleagues see until a change is approved. */
    val approvedGrade: String? = null,
    val gradePending: Boolean = false,
) {
    val input get() = ProfileInput(fullName, specialty, hospital, licenseNumber, phone)
    val invalidFields: Set<ProfileField> get() = ProfileValidator.validate(input)
    val isStaff: Boolean get() = accountType == AccountType.STAFF
    val facilityMissing: Boolean get() = isStaff && facilityId == null
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository,
    private val imageCompressor: ImageCompressor,
    private val patientRepository: PatientRepository,
    private val facilityRepository: FacilityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        doctorRepository.myProfile.value?.let { fill(it) }
        viewModelScope.launch { facilityRepository.facilities.collect { list -> _state.update { it.copy(facilities = list) } } }
        viewModelScope.launch { facilityRepository.refresh() }
    }

    private fun fill(doctor: Doctor) {
        _state.update {
            it.copy(
                fullName = doctor.fullName,
                specialty = doctor.specialty.orEmpty(),
                hospital = doctor.hospital.orEmpty(),
                licenseNumber = doctor.licenseNumber.orEmpty(),
                phone = doctor.phone.orEmpty(),
                language = doctor.preferredLanguage,
                email = doctor.email,
                verificationStatus = doctor.verificationStatus,
                verificationNote = doctor.verificationNote,
                hasLicenseDocument = doctor.licenseDocumentPath != null,
                accountType = doctor.accountType,
                savedAccountType = doctor.accountType,
                facilityId = doctor.facilityId,
                // The dropdown shows the grade asked for, if a change is waiting.
                grade = doctor.requestedGrade ?: doctor.grade,
                approvedGrade = doctor.grade,
                gradePending = doctor.requestedGrade != null,
            )
        }
        doctor.photoPath?.let { path ->
            viewModelScope.launch { _state.update { it.copy(photoUrl = doctorRepository.photoUrl(path)) } }
        }
    }

    /** Refreshes read-only fields (status, photo) without overwriting what the user is typing. */
    private fun refreshStatus(doctor: Doctor) {
        _state.update {
            it.copy(
                verificationStatus = doctor.verificationStatus,
                verificationNote = doctor.verificationNote,
                hasLicenseDocument = doctor.licenseDocumentPath != null,
            )
        }
    }

    fun onFullNameChange(v: String) = _state.update { it.copy(fullName = v, error = null) }
    fun onSpecialtyChange(v: String) = _state.update { it.copy(specialty = v, error = null) }
    fun onHospitalChange(v: String) = _state.update { it.copy(hospital = v, error = null) }
    fun onLicenseChange(v: String) = _state.update { it.copy(licenseNumber = v, error = null) }
    fun onPhoneChange(v: String) = _state.update { it.copy(phone = v, error = null) }
    fun onLanguageChange(v: String) = _state.update { it.copy(language = v) }
    fun onAccountTypeChange(v: AccountType) = _state.update { it.copy(accountType = v, error = null) }
    fun onFacilityChange(id: String?) = _state.update { it.copy(facilityId = id, error = null) }
    fun onGradeChange(v: String?) = _state.update { it.copy(grade = v, error = null) }

    fun addFacility(name: String, kind: String, hospital: String) {
        viewModelScope.launch {
            facilityRepository.add(name, kind, hospital)
                .onSuccess { f -> _state.update { it.copy(facilityId = f.id) } }
                .onFailure { e -> _state.update { it.copy(error = e.toDataError()) } }
        }
    }

    fun save() {
        val s = _state.value
        if (s.invalidFields.isNotEmpty() || s.facilityMissing) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            doctorRepository.updateMyProfile(s.input, s.language, s.accountType, s.facilityId, s.grade)
                .onSuccess { doctor ->
                    refreshStatus(doctor)
                    _state.update {
                        it.copy(savedAccountType = doctor.accountType, approvedGrade = doctor.grade,
                            gradePending = doctor.requestedGrade != null)
                    }
                    _state.update { it.copy(saving = false, savedEvent = true) }
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.toDataError()) } }
        }
    }

    fun onPhotoPicked(uri: Uri) {
        _state.update { it.copy(uploadingPhoto = true, error = null) }
        viewModelScope.launch {
            runCatching { imageCompressor.toJpeg(uri, maxDimension = 512) }
                .mapCatching { doctorRepository.uploadMyPhoto(it).getOrThrow() }
                .onSuccess { doctor ->
                    val url = doctor.photoPath?.let { doctorRepository.photoUrl(it) }
                    _state.update { it.copy(uploadingPhoto = false, photoUrl = url) }
                }
                .onFailure { e -> _state.update { it.copy(uploadingPhoto = false, error = e.toDataError()) } }
        }
    }

    fun onLicensePhotoPicked(uri: Uri) {
        _state.update { it.copy(uploadingLicense = true, error = null) }
        viewModelScope.launch {
            runCatching { imageCompressor.toJpeg(uri, maxDimension = 2000, quality = 90) }
                .mapCatching { doctorRepository.uploadMyLicenseDocument(it).getOrThrow() }
                .onSuccess { doctor ->
                    refreshStatus(doctor)
                    _state.update { it.copy(uploadingLicense = false) }
                }
                .onFailure { e -> _state.update { it.copy(uploadingLicense = false, error = e.toDataError()) } }
        }
    }

    fun onSavedEventHandled() = _state.update { it.copy(savedEvent = false) }

    /** Signing out wipes the offline copy, so warn first if some changes haven't been uploaded. */
    fun requestSignOut() {
        viewModelScope.launch {
            val unsynced = patientRepository.unsyncedChangeCount()
            _state.update { if (unsynced > 0) it.copy(signOutWarning = unsynced) else it.copy(signOutConfirmed = true) }
        }
    }

    fun confirmSignOut() = _state.update { it.copy(signOutWarning = null, signOutConfirmed = true) }
    fun cancelSignOut() = _state.update { it.copy(signOutWarning = null) }
    fun onSignOutHandled() = _state.update { it.copy(signOutConfirmed = false) }
}
