package com.myclinic.domain.record

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * The patient record, one class per database table. Property names are the
 * database column names in camelCase; [RecordJson] converts them to
 * snake_case automatically (patientId <-> patient_id).
 *
 * Dates are kept as ISO text exactly as the server sends them
 * ("2026-09-30" for dates, "2026-09-30T08:15:00+00:00" for timestamps) and
 * parsed only for display and sorting (see [Dates]).
 */
val RecordJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true // a cleared field must be sent as null to clear it on the server
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/** Columns every record entry shares. */
interface RecordEntry {
    val id: String
    val patientId: String
    val createdBy: String?
    val createdAt: String?
    val updatedAt: String?
    val deletedAt: String?
    val isDeleted: Boolean get() = deletedAt != null
}

@Serializable
data class Patient(
    val id: String,
    val ownerId: String,
    val fullName: String,
    val dateOfBirth: String? = null,
    val ageYears: Int? = null,
    val sex: String = "unknown",
    val nationalId: String? = null,
    val fileNumber: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val primaryDiagnosis: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val deletedAt: String? = null,
    /** Set when the doctor marks the patient "discharged / follow-up finished". Not a deletion. */
    val dischargedAt: String? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
    val isDischarged: Boolean get() = dischargedAt != null
}

@Serializable
data class PresentingComplaint(
    override val id: String,
    override val patientId: String,
    val complaint: String,
    val hpi: String? = null,
    val onsetDate: String? = null,
    val recordedAt: String? = null,
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry

@Serializable
data class MedicalCondition(
    override val id: String,
    override val patientId: String,
    val conditionCode: String? = null,
    val name: String,
    val isChronic: Boolean = true,
    val diagnosedOn: String? = null,
    val notes: String? = null,
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry

@Serializable
data class SurgicalHistoryItem(
    override val id: String,
    override val patientId: String,
    val procedure: String,
    val performedOn: String? = null,
    val hospital: String? = null,
    val complications: String? = null,
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry

@Serializable
data class Medication(
    override val id: String,
    override val patientId: String,
    val name: String,
    val dose: String? = null,
    val route: String? = null,
    val frequency: String? = null,
    val startedOn: String? = null,
    val stoppedOn: String? = null,
    val isCurrent: Boolean = true,
    val notes: String? = null,
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry

@Serializable
data class Allergy(
    override val id: String,
    override val patientId: String,
    val allergen: String,
    val reaction: String? = null,
    val severity: String = "moderate",
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry

@Serializable
data class FamilyHistoryItem(
    override val id: String,
    override val patientId: String,
    val relation: String,
    val condition: String,
    val notes: String? = null,
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry

@Serializable
data class SocialHistory(
    override val id: String,
    override val patientId: String,
    val smoking: String = "unknown",
    val packYears: Double? = null,
    val alcohol: String? = null,
    val occupation: String? = null,
    val maritalStatus: String? = null,
    val notes: String? = null,
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry

@Serializable
data class Examination(
    override val id: String,
    override val patientId: String,
    val examinedAt: String? = null,
    val findings: String? = null,
    val pulseBpm: Int? = null,
    val systolicMmhg: Int? = null,
    val diastolicMmhg: Int? = null,
    val respRate: Int? = null,
    val temperatureC: Double? = null,
    val spo2Percent: Int? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val painScore: Int? = null,
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry

@Serializable
data class SurgicalCase(
    override val id: String,
    override val patientId: String,
    val diagnosis: String,
    val plannedOperation: String? = null,
    val plannedDate: String? = null,
    val status: String = "planned",
    val preopChecklist: Map<String, Boolean> = emptyMap(),
    val operationDate: String? = null,
    val operativeNotes: String? = null,
    val complications: String? = null,
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry {
    val isUpcoming: Boolean get() = status == "planned" || status == "scheduled"
}

@Serializable
data class PostopFollowup(
    override val id: String,
    override val patientId: String,
    val surgicalCaseId: String,
    val visitDate: String? = null,
    val woundStatus: String? = null,
    val notes: String? = null,
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry

@Serializable
data class InvestigationRequest(
    override val id: String,
    override val patientId: String,
    val kind: String = "lab",
    val tests: List<String> = emptyList(),
    val urgency: String = "routine",
    val clinicalNotes: String? = null,
    /** The department inbox it was sent to; null when the doctor handles it. */
    val facilityId: String? = null,
    val status: String = InvestigationStatus.REQUESTED,
    val requestedAt: String? = null,
    val sampleTakenAt: String? = null,
    val resultedAt: String? = null,
    val reviewedAt: String? = null,
    val reviewedBy: String? = null,
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry

/** One typed value of a result, e.g. Hb 11.2 g/dL (reference 13–17). */
@Serializable
data class LabValue(
    val test: String,
    val value: Double? = null,
    /** For non-numeric results ("positive", "trace"). */
    val text: String? = null,
    val unit: String? = null,
    val low: Double? = null,
    val high: Double? = null,
) {
    val isAbnormal: Boolean
        get() = value != null && ((low != null && value < low) || (high != null && value > high))
}

@Serializable
data class InvestigationResult(
    override val id: String,
    override val patientId: String,
    val requestId: String? = null,
    val kind: String = "lab",
    val title: String,
    val resultDate: String? = null,
    val reportText: String? = null,
    val labValues: List<LabValue> = emptyList(),
    /** "staff" = uploaded by the lab/radiology department; such results can't be edited. */
    val source: String = "doctor",
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry {
    val isFromDepartment: Boolean get() = source == "staff"
}

@Serializable
data class Attachment(
    override val id: String,
    override val patientId: String,
    /** "investigations" or "surgical_care". */
    val section: String,
    val resultId: String? = null,
    val followupId: String? = null,
    val storagePath: String,
    val mimeType: String,
    val fileName: String? = null,
    val caption: String? = null,
    val takenAt: String? = null,
    override val createdBy: String? = null,
    override val createdAt: String? = null,
    override val updatedAt: String? = null,
    override val deletedAt: String? = null,
) : RecordEntry {
    val isPdf: Boolean get() = mimeType == "application/pdf"
}

/** Everything recorded for one patient, as held in the offline cache. Deleted entries are excluded. */
data class PatientRecord(
    val patient: Patient,
    val complaints: List<PresentingComplaint> = emptyList(),
    val conditions: List<MedicalCondition> = emptyList(),
    val surgicalHistory: List<SurgicalHistoryItem> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val allergies: List<Allergy> = emptyList(),
    val familyHistory: List<FamilyHistoryItem> = emptyList(),
    val socialHistory: SocialHistory? = null,
    val examinations: List<Examination> = emptyList(),
    val surgicalCases: List<SurgicalCase> = emptyList(),
    val followups: List<PostopFollowup> = emptyList(),
    val investigationRequests: List<InvestigationRequest> = emptyList(),
    val investigationResults: List<InvestigationResult> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
) {
    fun resultsFor(request: InvestigationRequest): List<InvestigationResult> =
        investigationResults.filter { it.requestId == request.id }

    fun attachmentsForResult(resultId: String): List<Attachment> = attachments.filter { it.resultId == resultId }

    fun attachmentsForFollowup(followupId: String): List<Attachment> = attachments.filter { it.followupId == followupId }
}
