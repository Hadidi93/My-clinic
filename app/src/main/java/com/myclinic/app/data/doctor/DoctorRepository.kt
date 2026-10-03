package com.myclinic.app.data.doctor

import com.myclinic.domain.model.AccountType
import com.myclinic.domain.model.Doctor
import com.myclinic.domain.model.VerificationStatus
import com.myclinic.domain.validation.ProfileInput
import kotlinx.coroutines.flow.StateFlow

/** Reading and editing doctor profiles, plus the admin approval actions. */
interface DoctorRepository {
    /** The signed-in doctor's profile, or null until loaded / after sign-out. */
    val myProfile: StateFlow<Doctor?>

    suspend fun refreshMyProfile(): Result<Doctor>
    /** [facilityId] is the department for staff accounts (null for doctors). */
    suspend fun updateMyProfile(
        input: ProfileInput,
        language: String,
        accountType: AccountType,
        facilityId: String?,
        grade: String?,
    ): Result<Doctor>
    suspend fun uploadMyPhoto(jpegBytes: ByteArray): Result<Doctor>
    suspend fun uploadMyLicenseDocument(jpegBytes: ByteArray): Result<Doctor>

    /** Short-lived links for showing private images. Null if not available. */
    suspend fun photoUrl(path: String): String?
    suspend fun licenseDocumentUrl(path: String): String?

    // Admin only (the server refuses these for everyone else).
    suspend fun doctorsAwaitingVerification(): Result<List<Doctor>>
    suspend fun setVerification(doctorId: String, status: VerificationStatus, note: String?): Result<Unit>
    /** Approve or reject a doctor's requested grade. */
    suspend fun reviewGrade(doctorId: String, approve: Boolean): Result<Unit>

    fun clear()
}
