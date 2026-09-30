package com.myclinic.domain.permissions

import com.myclinic.domain.TestData
import com.myclinic.domain.TestData.NOW
import com.myclinic.domain.model.ConsultStatus
import com.myclinic.domain.model.PatientRef
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.model.ReferralGrant
import com.myclinic.domain.model.ReferralKind
import com.myclinic.domain.model.ReferralStatus
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
    fun `only the owner can edit, a consultant cannot`() {
        assertTrue(AccessPolicy.canEdit(owner, patient))
        assertFalse(AccessPolicy.canEdit(consultant, patient, emptyList()))
    }

    private fun referral(
        kind: ReferralKind = ReferralKind.COMANAGEMENT,
        status: ReferralStatus = ReferralStatus.ACCEPTED,
        toId: String = "consultant",
    ) = ReferralGrant("ref-1", "patient-1", "owner", toId, kind, status)

    @Test
    fun `accepted co-manager can read every section and edit`() {
        val refs = listOf(referral())
        assertTrue(AccessPolicy.canEdit(consultant, patient, refs))
        assertEquals(
            RecordSection.entries.toSet(),
            AccessPolicy.readableSections(consultant, patient, emptyList(), NOW, refs),
        )
    }

    @Test
    fun `co-manager cannot delete, share or transfer the patient`() {
        val refs = listOf(referral())
        assertTrue(AccessPolicy.canEdit(consultant, patient, refs))
        assertFalse(AccessPolicy.canManagePatient("consultant", patient))
        assertFalse(AccessPolicy.canShare(consultant, patient))
    }

    @Test
    fun `pending, declined or ended referrals give nothing`() {
        listOf(ReferralStatus.PENDING, ReferralStatus.DECLINED, ReferralStatus.CANCELLED, ReferralStatus.ENDED).forEach {
            val refs = listOf(referral(status = it))
            assertFalse(AccessPolicy.canEdit(consultant, patient, refs))
            assertFalse(AccessPolicy.canReadSection(consultant, patient, RecordSection.ALLERGIES, emptyList(), NOW, refs))
        }
    }

    @Test
    fun `a pending transfer gives no access`() {
        val refs = listOf(referral(kind = ReferralKind.TRANSFER, status = ReferralStatus.PENDING))
        assertFalse(AccessPolicy.canEdit(consultant, patient, refs))
    }

    @Test
    fun `unverified co-manager and deleted patient give no access`() {
        val refs = listOf(referral())
        val pendingConsultant = TestData.doctor("consultant", VerificationStatus.PENDING)
        assertFalse(AccessPolicy.canEdit(pendingConsultant, patient, refs))
        assertFalse(AccessPolicy.canEdit(consultant, patient.copy(deleted = true), refs))
    }

    @Test
    fun `closing a consult does not affect co-management`() {
        val closedConsult = TestData.grant(status = ConsultStatus.CLOSED)
        val refs = listOf(referral())
        assertTrue(
            AccessPolicy.canReadSection(consultant, patient, RecordSection.MEDICATIONS, listOf(closedConsult), NOW, refs),
        )
        assertFalse(
            AccessPolicy.canReadSection(consultant, patient, RecordSection.MEDICATIONS, listOf(closedConsult), NOW),
        )
    }

    @Test
    fun `unverified owner can manage their patients but not share`() {
        val pendingOwner = TestData.doctor("owner", VerificationStatus.PENDING)
        assertTrue(AccessPolicy.canEdit(pendingOwner, patient))
        assertFalse(AccessPolicy.canShare(pendingOwner, patient))
        assertTrue(AccessPolicy.canShare(owner, patient))
    }

    @Test
    fun `only the requester can revoke, and only once`() {
        assertTrue(AccessPolicy.canRevoke("owner", TestData.grant()))
        assertFalse(AccessPolicy.canRevoke("consultant", TestData.grant()))
        assertFalse(AccessPolicy.canRevoke("owner", TestData.grant(revokedAt = NOW)))
    }
}
