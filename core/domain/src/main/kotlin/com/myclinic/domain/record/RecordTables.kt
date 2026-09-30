package com.myclinic.domain.record

import com.myclinic.domain.model.RecordSection
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

/**
 * Every database table the app keeps offline, in the order they must be
 * uploaded (a patient before its entries, a surgical case before its
 * follow-ups). [section] is the Consult checkbox the table belongs to.
 */
enum class RecordTable(val tableName: String, val section: RecordSection) {
    PATIENTS("patients", RecordSection.IDENTIFIERS),
    PRESENTING_COMPLAINTS("presenting_complaints", RecordSection.PRESENTING_COMPLAINT),
    MEDICAL_CONDITIONS("medical_conditions", RecordSection.PAST_MEDICAL),
    SURGICAL_HISTORY("surgical_history", RecordSection.PAST_SURGICAL),
    MEDICATIONS("medications", RecordSection.MEDICATIONS),
    ALLERGIES("allergies", RecordSection.ALLERGIES),
    FAMILY_HISTORY("family_history", RecordSection.FAMILY_HISTORY),
    SOCIAL_HISTORY("social_history", RecordSection.SOCIAL_HISTORY),
    EXAMINATIONS("examinations", RecordSection.EXAMINATION),
    SURGICAL_CASES("surgical_cases", RecordSection.SURGICAL_CARE),
    POSTOP_FOLLOWUPS("postop_followups", RecordSection.SURGICAL_CARE);

    companion object {
        fun fromTableName(name: String): RecordTable? = entries.firstOrNull { it.tableName == name }
    }
}

/** Columns the server fills in itself; they are never uploaded. */
val SERVER_MANAGED_COLUMNS = setOf("created_at", "updated_at")

/** Removes server-managed columns before an upload. */
fun JsonObject.forUpload(): JsonObject = JsonObject(filterKeys { it !in SERVER_MANAGED_COLUMNS })

/** One cached row: which table it came from and its JSON as the server sends it. */
data class CachedRow(val table: RecordTable, val json: JsonObject)

/**
 * Builds a [PatientRecord] from the cached rows of one patient.
 * Rows that can't be read (e.g. from a newer app version) are skipped
 * rather than crashing the screen. Deleted entries are left out.
 */
object PatientRecordAssembler {

    fun assemble(patientJson: JsonObject, rows: List<CachedRow>): PatientRecord? {
        val patient = decode(Patient.serializer(), patientJson) ?: return null
        fun <T : RecordEntry> list(table: RecordTable, serializer: KSerializer<T>): List<T> =
            rows.asSequence()
                .filter { it.table == table }
                .mapNotNull { decode(serializer, it.json) }
                .filterNot { it.isDeleted }
                .toList()

        return PatientRecord(
            patient = patient,
            complaints = list(RecordTable.PRESENTING_COMPLAINTS, PresentingComplaint.serializer())
                .sortedByDescending { Dates.sortKey(it.recordedAt ?: it.createdAt) },
            conditions = list(RecordTable.MEDICAL_CONDITIONS, MedicalCondition.serializer()),
            surgicalHistory = list(RecordTable.SURGICAL_HISTORY, SurgicalHistoryItem.serializer())
                .sortedByDescending { Dates.sortKey(it.performedOn) },
            medications = list(RecordTable.MEDICATIONS, Medication.serializer())
                .sortedWith(compareByDescending<Medication> { it.isCurrent }.thenBy { it.name.lowercase() }),
            allergies = list(RecordTable.ALLERGIES, Allergy.serializer())
                .sortedByDescending { Severity.rank(it.severity) },
            familyHistory = list(RecordTable.FAMILY_HISTORY, FamilyHistoryItem.serializer()),
            socialHistory = list(RecordTable.SOCIAL_HISTORY, SocialHistory.serializer())
                .maxByOrNull { Dates.sortKey(it.updatedAt) },
            examinations = list(RecordTable.EXAMINATIONS, Examination.serializer())
                .sortedByDescending { Dates.sortKey(it.examinedAt ?: it.createdAt) },
            surgicalCases = list(RecordTable.SURGICAL_CASES, SurgicalCase.serializer())
                .sortedByDescending { Dates.sortKey(it.plannedDate ?: it.createdAt) },
            followups = list(RecordTable.POSTOP_FOLLOWUPS, PostopFollowup.serializer())
                .sortedByDescending { Dates.sortKey(it.visitDate) },
        )
    }

    fun <T> decode(serializer: KSerializer<T>, json: JsonObject): T? =
        runCatching { RecordJson.decodeFromJsonElement(serializer, json) }.getOrNull()
}

/** Allergy severity order, most dangerous first in the red banner. */
object Severity {
    val VALUES = listOf("mild", "moderate", "severe", "life_threatening")
    fun rank(value: String): Int = VALUES.indexOf(value)
}
