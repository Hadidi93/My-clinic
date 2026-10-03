package com.myclinic.domain.record

import kotlinx.serialization.Serializable

/** One line of an access log (database functions patient_access_log / admin_activity_log). */
@Serializable
data class AuditEntry(
    val id: Long,
    val occurredAt: String,
    /** view, create, update, delete, share, revoke, close, export, verify, refer, transfer, submit_result */
    val action: String,
    val tableName: String,
    val recordId: String? = null,
    val patientId: String? = null,
    val actorId: String? = null,
    val actorName: String? = null,
    val actorGrade: String? = null,
    val actorIsStaff: Boolean = false,
    val actorIsMe: Boolean = false,
    /** "consult" or "staff" when the access came through a consult or a lab request. */
    val via: String? = null,
    /** For views: which section was opened. */
    val section: String? = null,
)
