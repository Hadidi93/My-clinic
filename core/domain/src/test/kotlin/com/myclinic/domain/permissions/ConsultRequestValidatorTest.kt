package com.myclinic.domain.permissions

import com.myclinic.domain.TestData
import com.myclinic.domain.model.PatientRef
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.model.VerificationStatus
import com.myclinic.domain.permissions.ConsultRequestError.ANONYMIZED_WITH_IDENTIFIERS
import com.myclinic.domain.permissions.ConsultRequestError.CANNOT_CONSULT_SELF
import com.myclinic.domain.permissions.ConsultRequestError.CONSENT_DATE_IN_FUTURE
import com.myclinic.domain.permissions.ConsultRequestError.CONSENT_MISSING
import com.myclinic.domain.permissions.ConsultRequestError.CONSULTANT_NOT_VERIFIED
import com.myclinic.domain.permissions.ConsultRequestError.INVALID_DURATION
import com.myclinic.domain.permissions.ConsultRequestError.NOT_PATIENT_OWNER
import com.myclinic.domain.permissions.ConsultRequestError.NO_SECTIONS
import com.myclinic.domain.permissions.ConsultRequestError.QUESTION_EMPTY
import com.myclinic.domain.permissions.ConsultRequestError.REQUESTER_NOT_VERIFIED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConsultRequestValidatorTest {
    private val today = LocalDate.of(2026, 9, 30)
    private val owner = TestData.doctor("owner")
    private val consultant = TestData.doctor("consultant")

    private fun request(
        consultant: com.myclinic.domain.model.Doctor = this.consultant,
        ownerId: String = "owner",
        question: String = "Please advise on management of this demo case.",
        sections: Set<RecordSection> = setOf(RecordSection.INVESTIGATIONS),
        anonymize: Boolean = true,
        durationDays: Int = 7,
        consentConfirmed: Boolean = true,
        consentDate: LocalDate? = today,
    ) = ConsultRequest(
        patient = PatientRef("patient-1", ownerId),
        consultant = consultant,
        question = question,
        sections = sections,
        anonymize = anonymize,
        durationDays = durationDays,
        consentConfirmed = consentConfirmed,
        consentDate = consentDate,
    )

    @Test
    fun `a complete request passes`() {
        assertTrue(ConsultRequestValidator.validate(owner, request(), today).isEmpty())
    }

    @Test
    fun `unverified requester cannot share`() {
        val pending = TestData.doctor("owner", VerificationStatus.PENDING)
        assertEquals(listOf(REQUESTER_NOT_VERIFIED), ConsultRequestValidator.validate(pending, request(), today))
    }

    @Test
    fun `cannot share someone else's patient`() {
        assertEquals(listOf(NOT_PATIENT_OWNER), ConsultRequestValidator.validate(owner, request(ownerId = "x"), today))
    }

    @Test
    fun `cannot consult yourself`() {
        assertEquals(listOf(CANNOT_CONSULT_SELF), ConsultRequestValidator.validate(owner, request(consultant = owner), today))
    }

    @Test
    fun `consultant must be verified`() {
        val pending = TestData.doctor("consultant", VerificationStatus.PENDING)
        assertEquals(
            listOf(CONSULTANT_NOT_VERIFIED),
            ConsultRequestValidator.validate(owner, request(consultant = pending), today),
        )
    }

    @Test
    fun `question and at least one section are required`() {
        assertEquals(
            listOf(QUESTION_EMPTY, NO_SECTIONS),
            ConsultRequestValidator.validate(owner, request(question = "   ", sections = emptySet()), today),
        )
    }

    @Test
    fun `anonymized consult cannot include identifiers`() {
        val errors = ConsultRequestValidator.validate(
            owner,
            request(anonymize = true, sections = setOf(RecordSection.IDENTIFIERS)),
            today,
        )
        assertEquals(listOf(ANONYMIZED_WITH_IDENTIFIERS), errors)
        // Not anonymized: identifiers are allowed.
        assertTrue(
            ConsultRequestValidator.validate(
                owner, request(anonymize = false, sections = setOf(RecordSection.IDENTIFIERS)), today,
            ).isEmpty(),
        )
    }

    @Test
    fun `duration must be between 1 and 90 days`() {
        listOf(0, -1, 91).forEach {
            assertEquals(listOf(INVALID_DURATION), ConsultRequestValidator.validate(owner, request(durationDays = it), today))
        }
        listOf(1, 7, 90).forEach {
            assertTrue(ConsultRequestValidator.validate(owner, request(durationDays = it), today).isEmpty())
        }
    }

    @Test
    fun `consent checkbox and date are required, and the date cannot be in the future`() {
        assertEquals(listOf(CONSENT_MISSING), ConsultRequestValidator.validate(owner, request(consentConfirmed = false), today))
        assertEquals(listOf(CONSENT_MISSING), ConsultRequestValidator.validate(owner, request(consentDate = null), today))
        assertEquals(
            listOf(CONSENT_DATE_IN_FUTURE),
            ConsultRequestValidator.validate(owner, request(consentDate = today.plusDays(1)), today),
        )
    }
}
