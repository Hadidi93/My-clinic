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
