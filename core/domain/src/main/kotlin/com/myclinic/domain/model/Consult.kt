package com.myclinic.domain.model

import java.time.Instant

enum class ConsultStatus(val dbValue: String) {
    PENDING("pending"),
    ANSWERED("answered"),
    CLOSED("closed");

    companion object {
        fun fromDb(value: String?): ConsultStatus = entries.firstOrNull { it.dbValue == value } ?: PENDING
    }
}

/**
 * One consult: the requesting doctor lets [consultantId] see [sections] of a
 * patient's record until [expiresAt], unless revoked or closed sooner.
 */
data class ConsultGrant(
    val consultId: String,
    val patientId: String,
    val requesterId: String,
    val consultantId: String,
    val sections: Set<RecordSection>,
    val anonymized: Boolean,
    val status: ConsultStatus,
    val expiresAt: Instant,
    val revokedAt: Instant?,
)

/** The minimum the permission rules need to know about a patient. */
data class PatientRef(
    val id: String,
    val ownerId: String,
    val deleted: Boolean = false,
)

enum class ReferralKind(val dbValue: String) {
    /** Colleague reads and edits the whole record alongside the owner. */
    COMANAGEMENT("comanagement"),

    /** Colleague becomes the owner once they accept. */
    TRANSFER("transfer");

    companion object {
        fun fromDb(value: String?): ReferralKind = entries.firstOrNull { it.dbValue == value } ?: COMANAGEMENT
    }
}

enum class ReferralStatus(val dbValue: String) {
    PENDING("pending"), ACCEPTED("accepted"), DECLINED("declined"), CANCELLED("cancelled"), ENDED("ended");

    companion object {
        fun fromDb(value: String?): ReferralStatus = entries.firstOrNull { it.dbValue == value } ?: PENDING
    }
}

/** A referral of [patientId] from [fromId] to [toId]. Mirrors the `referrals` table. */
data class ReferralGrant(
    val referralId: String,
    val patientId: String,
    val fromId: String,
    val toId: String,
    val kind: ReferralKind,
    val status: ReferralStatus,
)
