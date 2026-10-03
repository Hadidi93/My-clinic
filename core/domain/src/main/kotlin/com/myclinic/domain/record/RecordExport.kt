package com.myclinic.domain.record

import com.myclinic.domain.forms.ChronicDiseases
import com.myclinic.domain.forms.PreopChecklist
import com.myclinic.domain.forms.Vocabulary
import java.time.LocalDate

/** A printable record: a title block and sections of lines. Turned into a PDF by the app. */
data class RecordDocument(val title: String, val subtitle: List<String>, val sections: List<DocSection>)

data class DocSection(val heading: String, val lines: List<String>)

/**
 * Builds the printable version of a record in the chosen language, from the
 * same data the screens show. Deleted ("entered in error") entries are left
 * out, as on screen. Dates are passed as already-formatted text by the app.
 */
object RecordExport {

    fun build(
        record: PatientRecord,
        language: String,
        today: LocalDate,
        formatDate: (String?) -> String?,
        formatDateTime: (String?) -> String?,
        labels: ExportLabels,
    ): RecordDocument {
        fun opt(v: String?) = v?.let { Vocabulary.option(it).get(language) }
        fun field(k: String) = Vocabulary.field(k).get(language)
        fun section(t: RecordTable) = Vocabulary.section(t).get(language)
        fun join(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

        val p = record.patient
        val age = Dates.age(p.dateOfBirth, p.ageYears, today)
        val subtitle = listOfNotNull(
            join(age?.let { labels.ageYears(it) }, opt(p.sex).takeIf { p.sex != "unknown" }, p.fileNumber?.let { "${field("file_number")}: $it" }),
            p.primaryDiagnosis?.takeIf { it.isNotBlank() },
        ).filter { it.isNotBlank() }

        val sections = mutableListOf<DocSection>()
        fun add(heading: String, lines: List<String>) {
            if (lines.isNotEmpty()) sections += DocSection(heading, lines)
        }

        add(section(RecordTable.PATIENTS), listOfNotNull(
            p.dateOfBirth?.let { "${field("date_of_birth")}: ${formatDate(it)}" },
            p.nationalId?.let { "${field("national_id")}: $it" },
            p.phone?.let { "${field("phone")}: $it" },
            p.address?.let { "${field("address")}: $it" },
            p.emergencyContactName?.let { "${field("emergency_contact_name")}: ${join(it, p.emergencyContactPhone)}" },
        ))
        add(section(RecordTable.ALLERGIES), record.allergies.map { join(it.allergen, opt(it.severity), it.reaction) }
            .ifEmpty { listOf(labels.noKnownAllergies) })
        add(section(RecordTable.PRESENTING_COMPLAINTS), record.complaints.map {
            join(formatDateTime(it.recordedAt), it.complaint) + (it.hpi?.let { h -> "\n$h" } ?: "")
        })
        add(section(RecordTable.MEDICAL_CONDITIONS), record.conditions.map {
            join(ChronicDiseases.byCode(it.conditionCode)?.name?.get(language) ?: it.name, formatDate(it.diagnosedOn), it.notes)
        })
        add(section(RecordTable.SURGICAL_HISTORY), record.surgicalHistory.map {
            join(it.procedure, formatDate(it.performedOn), it.hospital, it.complications)
        })
        add(section(RecordTable.MEDICATIONS), record.medications.map {
            join(listOfNotNull(it.name, it.dose).joinToString(" "), opt(it.route), it.frequency,
                if (it.isCurrent) labels.current else labels.stopped)
        })
        add(section(RecordTable.FAMILY_HISTORY), record.familyHistory.map { join("${opt(it.relation)}: ${it.condition}", it.notes) })
        record.socialHistory?.let { s ->
            add(section(RecordTable.SOCIAL_HISTORY), listOf(join(
                "${field("smoking")}: ${opt(s.smoking)}", s.packYears?.let { "${field("pack_years")}: $it" },
                s.alcohol, s.occupation, opt(s.maritalStatus), s.notes,
            )))
        }
        add(section(RecordTable.EXAMINATIONS), record.examinations.map {
            join(formatDateTime(it.examinedAt), VitalsFormatter.summary(it)) + (it.findings?.let { f -> "\n$f" } ?: "")
        })
        add(labels.investigations, record.investigationRequests.map {
            join(formatDate(it.requestedAt), it.tests.joinToString(", "), opt(it.status))
        } + record.investigationResults.map { r ->
            join(formatDate(r.resultDate), r.title) +
                (LabValueFormatter.summary(r.labValues, max = 100)?.let { "\n$it" } ?: "") +
                (r.reportText?.let { "\n$it" } ?: "")
        })
        add(section(RecordTable.SURGICAL_CASES), record.surgicalCases.map { c ->
            val done = PreopChecklist.ITEMS.count { c.preopChecklist[it] == true }
            join(c.plannedOperation ?: c.diagnosis, c.diagnosis.takeIf { c.plannedOperation != null }, opt(c.status),
                formatDate(c.operationDate ?: c.plannedDate), labels.checklist(done, PreopChecklist.ITEMS.size)) +
                (c.operativeNotes?.let { "\n$it" } ?: "") + (c.complications?.let { "\n$it" } ?: "")
        })
        add(section(RecordTable.POSTOP_FOLLOWUPS), record.followups.map { join(formatDate(it.visitDate), opt(it.woundStatus), it.notes) })

        return RecordDocument(title = p.fullName, subtitle = subtitle, sections = sections)
    }
}

/** The few words the export needs that aren't record vocabulary (from the app's translations). */
data class ExportLabels(
    val ageYears: (Int) -> String,
    val noKnownAllergies: String,
    val current: String,
    val stopped: String,
    val investigations: String,
    val checklist: (done: Int, total: Int) -> String,
)
