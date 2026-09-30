package com.myclinic.domain.model

import com.myclinic.domain.validation.ProfileInput
import com.myclinic.domain.validation.ProfileValidator

/** Where a doctor's account is in the licence-verification process. Mirrors the SQL enum. */
enum class VerificationStatus(val dbValue: String) {
    /** New account, waiting for an administrator to check the licence. */
    PENDING("pending"),

    /** Licence checked. Only verified doctors can send or receive shared patient data. */
    VERIFIED("verified"),

    /** Licence could not be verified. The doctor can fix their details and resubmit. */
    REJECTED("rejected"),

    /** Access withdrawn by an administrator. */
    SUSPENDED("suspended");

    companion object {
        fun fromDb(value: String?): VerificationStatus =
            entries.firstOrNull { it.dbValue == value } ?: PENDING
    }
}

enum class AppRole(val dbValue: String) {
    DOCTOR("doctor"),
    ADMIN("admin");

    companion object {
        fun fromDb(value: String?): AppRole = entries.firstOrNull { it.dbValue == value } ?: DOCTOR
    }
}

/** A doctor's own profile, as stored in the `doctors` table. */
data class Doctor(
    val id: String,
    val email: String,
    val fullName: String,
    val specialty: String?,
    val hospital: String?,
    val licenseNumber: String?,
    val phone: String?,
    val photoPath: String?,
    val licenseDocumentPath: String?,
    val preferredLanguage: String,
    val role: AppRole,
    val verificationStatus: VerificationStatus,
    val verificationNote: String?,
) {
    val isVerified: Boolean get() = verificationStatus == VerificationStatus.VERIFIED

    /** Admin rights only count when the admin account itself is verified (same rule as the server). */
    val isAdmin: Boolean get() = role == AppRole.ADMIN && isVerified

    /** True once every field needed for licence verification has been filled in. */
    val isProfileComplete: Boolean
        get() = ProfileValidator.validate(
            ProfileInput(
                fullName = fullName,
                specialty = specialty.orEmpty(),
                hospital = hospital.orEmpty(),
                licenseNumber = licenseNumber.orEmpty(),
                phone = phone.orEmpty(),
            ),
        ).isEmpty()
}
