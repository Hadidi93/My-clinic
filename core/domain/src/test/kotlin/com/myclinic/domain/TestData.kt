package com.myclinic.domain

import com.myclinic.domain.model.AppRole
import com.myclinic.domain.model.ConsultGrant
import com.myclinic.domain.model.ConsultStatus
import com.myclinic.domain.model.Doctor
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.model.VerificationStatus
import java.time.Instant

/** Fake doctors and consults for tests. No real people or patients. */
object TestData {
    val NOW: Instant = Instant.parse("2026-09-30T10:00:00Z")

    fun doctor(
        id: String,
        status: VerificationStatus = VerificationStatus.VERIFIED,
        role: AppRole = AppRole.DOCTOR,
    ) = Doctor(
        id = id,
        email = "$id@example.test",
        fullName = "Dr Demo $id",
        specialty = "General Surgery",
        hospital = "Demo Hospital",
        licenseNumber = "LIC-$id",
        phone = "+201000000000",
        photoPath = null,
        licenseDocumentPath = null,
        preferredLanguage = "en",
        role = role,
        verificationStatus = status,
        verificationNote = null,
    )

    fun grant(
        patientId: String = "patient-1",
        requesterId: String = "owner",
        consultantId: String = "consultant",
        sections: Set<RecordSection> = setOf(RecordSection.INVESTIGATIONS, RecordSection.PAST_SURGICAL),
        anonymized: Boolean = false,
        status: ConsultStatus = ConsultStatus.PENDING,
        expiresAt: Instant = NOW.plusSeconds(7 * 24 * 3600),
        revokedAt: Instant? = null,
    ) = ConsultGrant(
        consultId = "consult-1",
        patientId = patientId,
        requesterId = requesterId,
        consultantId = consultantId,
        sections = sections,
        anonymized = anonymized,
        status = status,
        expiresAt = expiresAt,
        revokedAt = revokedAt,
    )
}
