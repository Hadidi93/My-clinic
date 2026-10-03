package com.myclinic.domain.model

import com.myclinic.domain.forms.Localized
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

/** A doctor's grade, shown to colleagues. Mirrors the check on doctors.grade. */
object DoctorGrade {
    val ALL = listOf("resident", "specialist", "consultant")
}

/** Common specialties, offered as quick picks so everyone spells them the same way (helps search). */
object Specialties {
    val ALL: List<Localized> = listOf(
        Localized("General Surgery", "الجراحة العامة"),
        Localized("Internal Medicine", "الباطنة"),
        Localized("Cardiology", "أمراض القلب"),
        Localized("Cardiothoracic Surgery", "جراحة القلب والصدر"),
        Localized("Orthopaedics", "العظام"),
        Localized("Neurosurgery", "جراحة المخ والأعصاب"),
        Localized("Neurology", "المخ والأعصاب"),
        Localized("Urology", "المسالك البولية"),
        Localized("Vascular Surgery", "جراحة الأوعية الدموية"),
        Localized("Plastic Surgery", "جراحة التجميل"),
        Localized("Paediatrics", "الأطفال"),
        Localized("Paediatric Surgery", "جراحة الأطفال"),
        Localized("Obstetrics & Gynaecology", "النساء والتوليد"),
        Localized("Anaesthesia", "التخدير"),
        Localized("Intensive Care", "الرعاية المركزة"),
        Localized("Emergency Medicine", "الطوارئ"),
        Localized("Gastroenterology", "الجهاز الهضمي"),
        Localized("Hepatology", "الكبد"),
        Localized("Nephrology", "الكلى"),
        Localized("Endocrinology", "الغدد الصماء"),
        Localized("Chest Diseases", "الأمراض الصدرية"),
        Localized("Oncology", "الأورام"),
        Localized("Haematology", "أمراض الدم"),
        Localized("Radiology", "الأشعة"),
        Localized("Clinical Pathology", "الباثولوجيا الإكلينيكية"),
        Localized("Dermatology", "الجلدية"),
        Localized("Ophthalmology", "الرمد"),
        Localized("ENT", "الأنف والأذن والحنجرة"),
        Localized("Psychiatry", "الطب النفسي"),
        Localized("Rheumatology", "الروماتيزم"),
        Localized("Family Medicine", "طب الأسرة"),
    )
}

/** Doctors manage patients; staff work in a lab/radiology department's inbox. Mirrors doctors.account_type. */
enum class AccountType(val dbValue: String) {
    DOCTOR("doctor"),
    STAFF("staff");

    companion object {
        fun fromDb(value: String?): AccountType = entries.firstOrNull { it.dbValue == value } ?: DOCTOR
    }
}

/**
 * A user's own profile, as stored in the `doctors` table. Lab/radiology staff
 * use the same table: [specialty] holds their job title and [licenseNumber]
 * their staff or syndicate ID, and they must belong to a department ([facilityId]).
 */
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
    val accountType: AccountType = AccountType.DOCTOR,
    val facilityId: String? = null,
    /** Doctors only: resident, specialist or consultant (see [DoctorGrade]). */
    val grade: String? = null,
) {
    val isStaff: Boolean get() = accountType == AccountType.STAFF

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
        ).isEmpty() && (!isStaff || facilityId != null)
}
