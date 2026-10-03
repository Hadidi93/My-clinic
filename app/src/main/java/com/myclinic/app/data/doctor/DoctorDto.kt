package com.myclinic.app.data.doctor

import com.myclinic.domain.model.AccountType
import com.myclinic.domain.model.AppRole
import com.myclinic.domain.model.Doctor
import com.myclinic.domain.model.VerificationStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A row of the `doctors` table exactly as the API returns it. */
@Serializable
data class DoctorDto(
    val id: String,
    val email: String,
    @SerialName("full_name") val fullName: String,
    val specialty: String? = null,
    val hospital: String? = null,
    @SerialName("license_number") val licenseNumber: String? = null,
    val phone: String? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("license_document_path") val licenseDocumentPath: String? = null,
    @SerialName("preferred_language") val preferredLanguage: String = "en",
    val role: String = "doctor",
    @SerialName("verification_status") val verificationStatus: String = "pending",
    @SerialName("verification_note") val verificationNote: String? = null,
    @SerialName("account_type") val accountType: String = "doctor",
    @SerialName("facility_id") val facilityId: String? = null,
    val grade: String? = null,
    @SerialName("requested_grade") val requestedGrade: String? = null,
) {
    fun toDomain() = Doctor(
        id = id,
        email = email,
        fullName = fullName,
        specialty = specialty,
        hospital = hospital,
        licenseNumber = licenseNumber,
        phone = phone,
        photoPath = photoPath,
        licenseDocumentPath = licenseDocumentPath,
        preferredLanguage = preferredLanguage,
        role = AppRole.fromDb(role),
        verificationStatus = VerificationStatus.fromDb(verificationStatus),
        verificationNote = verificationNote,
        accountType = AccountType.fromDb(accountType),
        facilityId = facilityId,
        grade = grade,
        requestedGrade = requestedGrade,
    )
}

/**
 * The profile fields a doctor may change. The database only allows updates
 * to these columns (see migration 1), so role/verification can't be sent.
 */
@Serializable
data class DoctorProfileUpdate(
    @SerialName("full_name") val fullName: String,
    val specialty: String,
    val hospital: String,
    @SerialName("license_number") val licenseNumber: String,
    val phone: String,
    @SerialName("preferred_language") val preferredLanguage: String,
    @SerialName("account_type") val accountType: String,
    @SerialName("facility_id") val facilityId: String?,
    /** The grade the doctor asks for; an admin approves it (see migration 9). */
    @SerialName("requested_grade") val requestedGrade: String?,
)

@Serializable
data class DoctorPhotoUpdate(@SerialName("photo_path") val photoPath: String)

@Serializable
data class DoctorLicenseDocumentUpdate(@SerialName("license_document_path") val licenseDocumentPath: String)

@Serializable
data class VerificationRequest(
    @SerialName("p_doctor_id") val doctorId: String,
    @SerialName("p_status") val status: String,
    @SerialName("p_note") val note: String?,
)
