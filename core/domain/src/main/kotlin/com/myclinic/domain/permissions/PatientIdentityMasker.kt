package com.myclinic.domain.permissions

import com.myclinic.domain.model.ConsultGrant
import com.myclinic.domain.model.RecordSection
import java.time.LocalDate
import java.time.Period

/** A patient's personal details as the owner sees them. */
data class PatientIdentity(
    val fullName: String,
    val dateOfBirth: LocalDate?,
    val ageYears: Int?,
    val sex: String,
    val nationalId: String?,
    val fileNumber: String?,
    val phone: String?,
    val address: String?,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
)

/**
 * What a consultant is allowed to see about who the patient is. Age and sex
 * are always included because they are clinically necessary. The name comes
 * with them unless the consult is anonymized. ID, file number, phone,
 * address and emergency contact also need "Personal data" to be shared.
 * A null [fullName] means the UI shows "Anonymous patient".
 */
data class ConsultantPatientView(
    val ageYears: Int?,
    val sex: String,
    val fullName: String?,
    val nationalId: String?,
    val fileNumber: String?,
    val phone: String?,
    val address: String?,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
)

/** Mirrors the server function `get_consult_patient`. */
object PatientIdentityMasker {

    fun ageOn(identity: PatientIdentity, today: LocalDate): Int? =
        identity.dateOfBirth?.let { Period.between(it, today).years } ?: identity.ageYears

    fun forConsultant(identity: PatientIdentity, grant: ConsultGrant, today: LocalDate): ConsultantPatientView {
        val showIdentity = !grant.anonymized && RecordSection.IDENTIFIERS in grant.sections
        return ConsultantPatientView(
            ageYears = ageOn(identity, today),
            sex = identity.sex,
            fullName = identity.fullName.takeIf { !grant.anonymized },
            nationalId = identity.nationalId.takeIf { showIdentity },
            fileNumber = identity.fileNumber.takeIf { showIdentity },
            phone = identity.phone.takeIf { showIdentity },
            address = identity.address.takeIf { showIdentity },
            emergencyContactName = identity.emergencyContactName.takeIf { showIdentity },
            emergencyContactPhone = identity.emergencyContactPhone.takeIf { showIdentity },
        )
    }
}
