package com.myclinic.domain.permissions

import com.myclinic.domain.model.ConsultGrant
import com.myclinic.domain.model.ConsultStatus
import com.myclinic.domain.model.Doctor
import com.myclinic.domain.model.PatientRef
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.model.ReferralGrant
import com.myclinic.domain.model.ReferralKind
import com.myclinic.domain.model.ReferralStatus
import java.time.Instant

/**
 * Who may see or change which part of a patient record.
 *
 * IMPORTANT: the real enforcement happens on the server, in the Row-Level
 * Security policies (function `can_read_section` in
 * supabase/migrations/..._consultations.sql). This Kotlin copy of the same
 * rules only decides what the app *shows* (hide buttons, grey out sections),
 * so the user never taps something the server would refuse. If you change a
 * rule here, change the SQL too; both have tests.
 */
object AccessPolicy {

    /** The owner ("primary doctor") is the only one who can delete, share, refer or transfer a patient. */
    fun isOwner(viewerId: String, patient: PatientRef): Boolean = patient.ownerId == viewerId

    /**
     * An accepted co-management by a verified colleague, for a patient that
     * isn't deleted. Mirrors `is_comanager` in the SQL.
     */
    fun isComanager(viewer: Doctor, patient: PatientRef, referrals: Collection<ReferralGrant>): Boolean =
        viewer.isVerified && !patient.deleted && referrals.any {
            it.patientId == patient.id && it.toId == viewer.id &&
                it.kind == ReferralKind.COMANAGEMENT && it.status == ReferralStatus.ACCEPTED
        }

    /** Owner or co-manager: may add and change record entries (mirrors `can_edit_patient`). */
    fun canEdit(viewer: Doctor, patient: PatientRef, referrals: Collection<ReferralGrant> = emptyList()): Boolean =
        isOwner(viewer.id, patient) || isComanager(viewer, patient, referrals)

    /** Deleting / restoring the patient, sharing it by consult, referring or transferring it: owner only. */
    fun canManagePatient(viewerId: String, patient: PatientRef): Boolean = isOwner(viewerId, patient)

    /** Sharing needs a verified owner (unverified doctors can manage their own patients but not share). */
    fun canShare(viewer: Doctor, patient: PatientRef): Boolean =
        isOwner(viewer.id, patient) && viewer.isVerified && !patient.deleted

    /**
     * A consult gives access only while it is live: not revoked, not closed,
     * not expired, and still issued by the patient's current owner.
     */
    fun isGrantActive(grant: ConsultGrant, patient: PatientRef, now: Instant): Boolean =
        grant.patientId == patient.id &&
            grant.requesterId == patient.ownerId &&
            grant.revokedAt == null &&
            grant.status != ConsultStatus.CLOSED &&
            now.isBefore(grant.expiresAt) &&
            !patient.deleted

    /**
     * Can [viewer] read [section] of [patient]?
     *  - The owner and accepted co-managers can read the whole record.
     *  - Anyone else needs an active consult that includes the section, and a
     *    verified account (unverified doctors never receive shared data).
     *  - Personal identifiers are never visible through an anonymized consult.
     */
    fun canReadSection(
        viewer: Doctor,
        patient: PatientRef,
        section: RecordSection,
        grants: Collection<ConsultGrant>,
        now: Instant,
        referrals: Collection<ReferralGrant> = emptyList(),
    ): Boolean {
        if (canEdit(viewer, patient, referrals)) return true
        if (!viewer.isVerified) return false
        return grants.any { grant ->
            grant.consultantId == viewer.id &&
                isGrantActive(grant, patient, now) &&
                section in grant.sections &&
                !(section == RecordSection.IDENTIFIERS && grant.anonymized)
        }
    }

    /** All sections [viewer] can currently read. Handy for building the read-only consult view. */
    fun readableSections(
        viewer: Doctor,
        patient: PatientRef,
        grants: Collection<ConsultGrant>,
        now: Instant,
        referrals: Collection<ReferralGrant> = emptyList(),
    ): Set<RecordSection> =
        RecordSection.entries.filterTo(mutableSetOf()) { canReadSection(viewer, patient, it, grants, now, referrals) }

    /** Only the doctor who created a consult can revoke it, and only while it is not already revoked. */
    fun canRevoke(viewerId: String, grant: ConsultGrant): Boolean =
        grant.requesterId == viewerId && grant.revokedAt == null
}
