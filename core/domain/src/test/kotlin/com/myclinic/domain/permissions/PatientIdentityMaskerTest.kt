package com.myclinic.domain.permissions

import com.myclinic.domain.TestData
import com.myclinic.domain.model.RecordSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PatientIdentityMaskerTest {
    private val today = LocalDate.of(2026, 9, 30)

    // Fake demo patient.
    private val identity = PatientIdentity(
        fullName = "Demo Patient One",
        dateOfBirth = LocalDate.of(1980, 10, 1),
        ageYears = null,
        sex = "female",
        nationalId = "00000000000000",
        fileNumber = "DEMO-001",
        phone = "+201000000001",
        address = "1 Demo Street",
        emergencyContactName = "Demo Relative",
        emergencyContactPhone = "+201000000002",
    )

    @Test
    fun `anonymized consult shows only age and sex`() {
        val grant = TestData.grant(sections = setOf(RecordSection.IDENTIFIERS), anonymized = true)
        val view = PatientIdentityMasker.forConsultant(identity, grant, today)
        assertEquals(45, view.ageYears) // birthday is tomorrow, so still 45
        assertEquals("female", view.sex)
        listOf(
            view.fullName, view.nationalId, view.fileNumber, view.phone,
            view.address, view.emergencyContactName, view.emergencyContactPhone,
        ).forEach { assertNull(it) }
    }

    @Test
    fun `identifiers not ticked means hidden even when not anonymized`() {
        val grant = TestData.grant(sections = setOf(RecordSection.ALLERGIES), anonymized = false)
        assertNull(PatientIdentityMasker.forConsultant(identity, grant, today).fullName)
    }

    @Test
    fun `identifiers ticked and not anonymized shows details`() {
        val grant = TestData.grant(sections = setOf(RecordSection.IDENTIFIERS), anonymized = false)
        val view = PatientIdentityMasker.forConsultant(identity, grant, today)
        assertEquals("Demo Patient One", view.fullName)
        assertEquals("DEMO-001", view.fileNumber)
    }

    @Test
    fun `falls back to recorded age when date of birth is unknown`() {
        val noDob = identity.copy(dateOfBirth = null, ageYears = 60)
        assertEquals(60, PatientIdentityMasker.ageOn(noDob, today))
    }
}
