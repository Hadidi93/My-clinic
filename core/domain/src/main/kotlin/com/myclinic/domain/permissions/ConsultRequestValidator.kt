package com.myclinic.domain.permissions

import com.myclinic.domain.model.Doctor
import com.myclinic.domain.model.PatientRef
import com.myclinic.domain.model.RecordSection
import java.time.LocalDate

/** What the doctor filled in on the "Consult" screen. */
data class ConsultRequest(
    val patient: PatientRef,
    val consultant: Doctor,
    val question: String,
    val sections: Set<RecordSection>,
    val anonymize: Boolean,
    val durationDays: Int,
    val consentConfirmed: Boolean,
    val consentDate: LocalDate?,
)

enum class ConsultRequestError {
    REQUESTER_NOT_VERIFIED,
    NOT_PATIENT_OWNER,
    PATIENT_DELETED,
    CANNOT_CONSULT_SELF,
    CONSULTANT_NOT_VERIFIED,
    QUESTION_EMPTY,
    QUESTION_TOO_LONG,
    NO_SECTIONS,
    ANONYMIZED_WITH_IDENTIFIERS,
    INVALID_DURATION,
    CONSENT_MISSING,
    CONSENT_DATE_IN_FUTURE,
}

/**
 * Checks a consult request before it is sent. The server function
 * `create_consult` repeats every one of these checks, so a modified app
 * cannot skip them. This copy gives instant, friendly feedback.
 */
object ConsultRequestValidator {
    const val MIN_DURATION_DAYS = 1
    const val MAX_DURATION_DAYS = 90
    const val MAX_QUESTION_LENGTH = 4000

    fun validate(requester: Doctor, request: ConsultRequest, today: LocalDate): List<ConsultRequestError> {
        val errors = mutableListOf<ConsultRequestError>()
        if (!requester.isVerified) errors += ConsultRequestError.REQUESTER_NOT_VERIFIED
        if (request.patient.ownerId != requester.id) errors += ConsultRequestError.NOT_PATIENT_OWNER
        if (request.patient.deleted) errors += ConsultRequestError.PATIENT_DELETED
        if (request.consultant.id == requester.id) errors += ConsultRequestError.CANNOT_CONSULT_SELF
        if (!request.consultant.isVerified) errors += ConsultRequestError.CONSULTANT_NOT_VERIFIED

        val question = request.question.trim()
        if (question.isEmpty()) errors += ConsultRequestError.QUESTION_EMPTY
        if (question.length > MAX_QUESTION_LENGTH) errors += ConsultRequestError.QUESTION_TOO_LONG

        if (request.sections.isEmpty()) errors += ConsultRequestError.NO_SECTIONS
        if (request.anonymize && RecordSection.IDENTIFIERS in request.sections) {
            errors += ConsultRequestError.ANONYMIZED_WITH_IDENTIFIERS
        }
        if (request.durationDays !in MIN_DURATION_DAYS..MAX_DURATION_DAYS) {
            errors += ConsultRequestError.INVALID_DURATION
        }
        if (!request.consentConfirmed || request.consentDate == null) {
            errors += ConsultRequestError.CONSENT_MISSING
        } else if (request.consentDate.isAfter(today)) {
            errors += ConsultRequestError.CONSENT_DATE_IN_FUTURE
        }
        return errors
    }
}
