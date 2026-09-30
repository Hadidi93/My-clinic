package com.myclinic.domain.permissions

import com.myclinic.domain.TestData
import com.myclinic.domain.TestData.NOW
import com.myclinic.domain.model.ConsultStatus
import com.myclinic.domain.model.PatientRef
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.model.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPolicyTest {
    private val owner = TestData.doctor("owner")
    private val consultant = TestData.doctor("consultant")
    private val stranger = TestData.doctor("stranger")
    private val patient = PatientRef(id = "patient-1", ownerId = "owner")

    @Test
    fun `owner can read every section of their own patient, even with no consults`() {
        RecordSection.entries.forEach { section ->
            assertTrue(AccessPolicy.canReadSection(owner, patient, section, emptyList(), NOW))
        }
    }

    @Test
    fun `owner keeps access even while their own account is pending verification`() {
        val pendingOwner = TestData.doctor("owner", VerificationStatus.PENDING)
        assertTrue(AccessPolicy.canReadSection(pendingOwner, patient, RecordSection.ALLERGIES, emptyList(), NOW))
    }

    @Test
    fun `a doctor with no consult cannot read anything`() {
        RecordSection.entries.forEach { section ->
            assertFalse(AccessPolicy.canReadSection(stranger, patient, section, listOf(TestData.grant()), NOW))
        }
    }

    @Test
    fun `consultant reads only the sections that were ticked`() {
        val readable = AccessPolicy.readableSections(consultant, patient, listOf(TestData.grant()), NOW)
        assertEquals(setOf(RecordSection.INVESTIGATIONS, RecordSection.PAST_SURGICAL), readable)
    }

    @Test
    fun `access ends at the expiry time`() {
        val grant = TestData.grant(expiresAt = NOW)
        assertFalse(AccessPolicy.canReadSection(consultant, patient, RecordSection.INVESTIGATIONS, listOf(grant), NOW))
        assertTrue(
            AccessPolicy.canReadSection(
                consultant, patient, RecordSection.INVESTIGATIONS, listOf(grant), NOW.minusSeconds(1),
            ),
        )
    }

    @Test
    fun `revoked consult gives no access`() {
        val grant = TestData.grant(revokedAt = NOW.minusSeconds(60))
        assertFalse(AccessPolicy.canReadSection(consultant, patient, RecordSection.INVESTIGATIONS, listOf(grant), NOW))
    }

    @Test
    fun `closed consult gives no access, answered consult still does`() {
        val closed = TestData.grant(status = ConsultStatus.CLOSED)
        val answered = TestData.grant(status = ConsultStatus.ANSWERED)
        assertFalse(AccessPolicy.canReadSection(consultant, patient, RecordSection.INVESTIGATIONS, listOf(closed), NOW))
        assertTrue(AccessPolicy.canReadSection(consultant, patient, RecordSection.INVESTIGATIONS, listOf(answered), NOW))
    }

    @Test
    fun `unverified or suspended consultant receives nothing`() {
        listOf(VerificationStatus.PENDING, VerificationStatus.REJECTED, VerificationStatus.SUSPENDED).forEach {
            val c = TestData.doctor("consultant", it)
            assertFalse(AccessPolicy.canReadSection(c, patient, RecordSection.INVESTIGATIONS, listOf(TestData.grant()), NOW))
        }
    }

    @Test
    fun `anonymized consult never exposes identifiers`() {
        val grant = TestData.grant(sections = setOf(RecordSection.IDENTIFIERS, RecordSection.ALLERGIES), anonymized = true)
        assertFalse(AccessPolicy.canReadSection(consultant, patient, RecordSection.IDENTIFIERS, listOf(grant), NOW))
        assertTrue(AccessPolicy.canReadSection(consultant, patient, RecordSection.ALLERGIES, listOf(grant), NOW))
    }

    @Test
    fun `consult for a different patient does not leak`() {
        val otherPatientGrant = TestData.grant(patientId = "patient-2")
        assertFalse(
            AccessPolicy.canReadSection(consultant, patient, RecordSection.INVESTIGATIONS, listOf(otherPatientGrant), NOW),
        )
    }

    @Test
    fun `consult created by someone who no longer owns the patient is ignored`() {
        val transferred = patient.copy(ownerId = "new-owner")
        assertFalse(
            AccessPolicy.canReadSection(consultant, transferred, RecordSection.INVESTIGATIONS, listOf(TestData.grant()), NOW),
        )
    }

    @Test
    fun `deleted patient is not visible to consultants`() {
        val deleted = patient.copy(deleted = true)
        assertFalse(
            AccessPolicy.canReadSection(consultant, deleted, RecordSection.INVESTIGATIONS, listOf(TestData.grant()), NOW),
        )
    }

    @Test
    fun `only the owner can edit`() {
        assertTrue(AccessPolicy.canEdit("owner", patient))
        assertFalse(AccessPolicy.canEdit("consultant", patient))
    }

    @Test
    fun `only the requester can revoke, and only once`() {
        assertTrue(AccessPolicy.canRevoke("owner", TestData.grant()))
        assertFalse(AccessPolicy.canRevoke("consultant", TestData.grant()))
        assertFalse(AccessPolicy.canRevoke("owner", TestData.grant(revokedAt = NOW)))
    }
}
