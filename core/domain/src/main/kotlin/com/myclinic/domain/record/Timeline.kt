package com.myclinic.domain.record

/** What happened, for choosing the icon and label on the timeline. */
enum class TimelineKind {
    PATIENT_ADDED,
    COMPLAINT,
    CONDITION_DIAGNOSED,
    PAST_SURGERY,
    MEDICATION_STARTED,
    MEDICATION_STOPPED,
    ALLERGY_RECORDED,
    EXAMINATION,
    OPERATION_PLANNED,
    OPERATION_DONE,
    FOLLOW_UP,
}

/**
 * One line on the patient timeline. [title] is the clinical text to show
 * (e.g. the complaint); the app adds the translated kind label and icon.
 */
data class TimelineEvent(
    val kind: TimelineKind,
    val at: String,
    val title: String,
    val detail: String? = null,
    val table: RecordTable,
    val recordId: String,
) {
    val sortKey: Long get() = Dates.sortKey(at)
}

/**
 * Builds the chronological story of a patient from every section.
 * Undated history (e.g. a past operation with no date) is left out of the
 * timeline but still appears in its section. Newest events come first.
 */
object TimelineBuilder {

    fun build(record: PatientRecord): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()
        fun add(kind: TimelineKind, at: String?, title: String, detail: String?, table: RecordTable, id: String) {
            if (!at.isNullOrBlank() && Dates.instant(at) != null) {
                events += TimelineEvent(kind, at, title, detail?.takeIf { it.isNotBlank() }, table, id)
            }
        }

        val p = record.patient
        add(TimelineKind.PATIENT_ADDED, p.createdAt, p.fullName, p.primaryDiagnosis, RecordTable.PATIENTS, p.id)

        record.complaints.forEach {
            add(TimelineKind.COMPLAINT, it.recordedAt ?: it.createdAt, it.complaint, it.hpi,
                RecordTable.PRESENTING_COMPLAINTS, it.id)
        }
        record.conditions.forEach {
            add(TimelineKind.CONDITION_DIAGNOSED, it.diagnosedOn, it.name, it.notes, RecordTable.MEDICAL_CONDITIONS, it.id)
        }
        record.surgicalHistory.forEach {
            add(TimelineKind.PAST_SURGERY, it.performedOn, it.procedure,
                listOfNotNull(it.hospital, it.complications).joinToString(" · "), RecordTable.SURGICAL_HISTORY, it.id)
        }
        record.medications.forEach {
            val detail = listOfNotNull(it.dose, it.frequency).joinToString(" ")
            add(TimelineKind.MEDICATION_STARTED, it.startedOn, it.name, detail, RecordTable.MEDICATIONS, it.id)
            add(TimelineKind.MEDICATION_STOPPED, it.stoppedOn, it.name, null, RecordTable.MEDICATIONS, it.id)
        }
        record.allergies.forEach {
            add(TimelineKind.ALLERGY_RECORDED, it.createdAt, it.allergen, it.reaction, RecordTable.ALLERGIES, it.id)
        }
        record.examinations.forEach {
            add(TimelineKind.EXAMINATION, it.examinedAt ?: it.createdAt, VitalsFormatter.summary(it) ?: "",
                it.findings, RecordTable.EXAMINATIONS, it.id)
        }
        record.surgicalCases.forEach {
            val operation = it.plannedOperation ?: it.diagnosis
            if (it.operationDate != null) {
                add(TimelineKind.OPERATION_DONE, it.operationDate, operation, it.complications, RecordTable.SURGICAL_CASES, it.id)
            }
            if (it.plannedDate != null && it.status != "cancelled" && it.operationDate == null) {
                add(TimelineKind.OPERATION_PLANNED, it.plannedDate, operation, it.diagnosis, RecordTable.SURGICAL_CASES, it.id)
            }
        }
        record.followups.forEach {
            add(TimelineKind.FOLLOW_UP, it.visitDate, it.woundStatus ?: "", it.notes, RecordTable.POSTOP_FOLLOWUPS, it.id)
        }

        return events.sortedWith(compareByDescending<TimelineEvent> { it.sortKey }.thenBy { it.kind.ordinal })
    }
}

/** Short text for a set of vital signs, e.g. "BP 120/80 · HR 88 · T 37.2 · SpO₂ 98%". */
object VitalsFormatter {
    fun summary(e: Examination): String? {
        val parts = buildList {
            if (e.systolicMmhg != null && e.diastolicMmhg != null) add("BP ${e.systolicMmhg}/${e.diastolicMmhg}")
            e.pulseBpm?.let { add("HR $it") }
            e.respRate?.let { add("RR $it") }
            e.temperatureC?.let { add("T $it") }
            e.spo2Percent?.let { add("SpO₂ $it%") }
            e.painScore?.let { add("Pain $it/10") }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
