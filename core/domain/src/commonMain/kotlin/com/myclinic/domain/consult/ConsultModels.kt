package com.myclinic.domain.consult

import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.record.RecordTable
import kotlinx.serialization.Serializable

/** Another doctor, as the directory and the lists show them. */
@Serializable
data class DoctorCard(
    val id: String,
    val fullName: String,
    val specialty: String? = null,
    val hospital: String? = null,
    val photoPath: String? = null,
    /** "resident", "specialist" or "consultant" (may be missing for older profiles). */
    val grade: String? = null,
) {
    /** Hospital and specialty, for places that show one line. */
    val subtitle: String get() = listOfNotNull(specialty, hospital).filter { it.isNotBlank() }.joinToString(" · ")
}

/** One consult in my list (from the database function `my_consults`). */
@Serializable
data class ConsultSummary(
    val id: String,
    val patientId: String,
    /** "requester" (I asked) or "consultant" (I was asked). */
    val role: String,
    val question: String,
    val urgency: String = "routine",
    val status: String = "pending",
    val anonymized: Boolean = false,
    val sections: List<String> = emptyList(),
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
    val closedAt: String? = null,
    /** Shared data is visible right now (not revoked, closed or expired). */
    val active: Boolean = false,
    val otherDoctor: DoctorCard,
    /** Null when the consultant may not see who the patient is. */
    val patientName: String? = null,
    val patientSex: String? = null,
    val patientAge: Int? = null,
    val lastActivityAt: String? = null,
    val unread: Int = 0,
) {
    val iAmConsultant: Boolean get() = role == "consultant"
    val sharedSections: Set<RecordSection> get() = sections.mapNotNull(RecordSection::fromDb).toSet()
}

@Serializable
data class ConsultMessage(
    val id: String,
    val consultId: String,
    val senderId: String,
    val body: String,
    val attachmentPaths: List<String> = emptyList(),
    val createdAt: String? = null,
)

/** What the consultant may know about who the patient is (database function `get_consult_patient`). */
@Serializable
data class ConsultPatient(
    val ageYears: Int? = null,
    val sex: String = "unknown",
    val anonymized: Boolean = true,
    val fullName: String? = null,
    val nationalId: String? = null,
    val fileNumber: String? = null,
    val phone: String? = null,
)

/** One referral in my list (database function `my_referrals`). */
@Serializable
data class ReferralSummary(
    val id: String,
    val patientId: String,
    /** "sent" or "received". */
    val direction: String,
    /** "comanagement" or "transfer". */
    val kind: String,
    val status: String,
    val note: String? = null,
    val createdAt: String? = null,
    val respondedAt: String? = null,
    val endedAt: String? = null,
    val otherDoctor: DoctorCard,
    val patientName: String,
    val patientSex: String? = null,
    val patientAge: Int? = null,
    val primaryDiagnosis: String? = null,
) {
    val received: Boolean get() = direction == "received"
}

/** An in-app notification (table `notifications`). Holds no patient data. */
@Serializable
data class AppNotification(
    val id: String,
    val kind: String,
    val refId: String? = null,
    val createdAt: String? = null,
    val readAt: String? = null,
) {
    val isRead: Boolean get() = readAt != null
}

/** The notification kinds, as written by the database triggers. */
object NotificationKind {
    const val CONSULT_REQUEST = "consult_request"
    const val CONSULT_MESSAGE = "consult_message"
    const val REFERRAL_REQUEST = "referral_request"
    const val REFERRAL_RESPONSE = "referral_response"
    const val LAB_REQUEST = "lab_request"

    val ALL = listOf(CONSULT_REQUEST, CONSULT_MESSAGE, REFERRAL_REQUEST, REFERRAL_RESPONSE, LAB_REQUEST)

    fun isConsult(kind: String) = kind == CONSULT_REQUEST || kind == CONSULT_MESSAGE
    fun isReferral(kind: String) = kind == REFERRAL_REQUEST || kind == REFERRAL_RESPONSE
}

/** What each side may do with a consult. The database functions enforce the same rules. */
object ConsultRules {
    /** Both doctors can write while the consult is open (not revoked, closed or expired). */
    fun canReply(c: ConsultSummary): Boolean = c.active

    /** Only the requesting doctor can withdraw access, while it is live. */
    fun canRevoke(c: ConsultSummary): Boolean = !c.iAmConsultant && c.active

    /** Either doctor can close an open consult (ends access to the record; the thread stays). */
    fun canClose(c: ConsultSummary): Boolean = c.status != "closed" && c.revokedAt == null

    /** The consultant can look at the shared parts of the record only while it is live. */
    fun canViewRecord(c: ConsultSummary): Boolean = c.iAmConsultant && c.active

    /** Sections ticked by default on a new consult: the usual minimum for an opinion. */
    val DEFAULT_SECTIONS: Set<RecordSection> = setOf(
        RecordSection.PRESENTING_COMPLAINT, RecordSection.PAST_MEDICAL, RecordSection.MEDICATIONS,
        RecordSection.ALLERGIES, RecordSection.EXAMINATION, RecordSection.INVESTIGATIONS,
    )

    /** Access lengths offered on the form, in days. */
    val DURATIONS = listOf(1, 3, 7, 14, 30, 90)

    /** The record tables a consultant should load for [sections] (each table belongs to one section). */
    fun tablesFor(sections: Set<RecordSection>): List<RecordTable> =
        RecordTable.entries.filter { t ->
            t != RecordTable.PATIENTS && when (t) {
                // Files belong to investigations (results) or surgical care (wound photos).
                RecordTable.ATTACHMENTS -> RecordSection.INVESTIGATIONS in sections || RecordSection.SURGICAL_CARE in sections
                else -> t.section in sections
            }
        }
}

/** What each side may do with a referral. Mirrors respond_referral / end_referral. */
object ReferralRules {
    fun canRespond(r: ReferralSummary): Boolean = r.received && r.status == "pending"
    fun canCancel(r: ReferralSummary): Boolean = !r.received && r.status == "pending"

    /** Either doctor can end an accepted co-management; a completed transfer can't be undone. */
    fun canEnd(r: ReferralSummary): Boolean = r.status == "accepted" && r.kind == "comanagement"
}
