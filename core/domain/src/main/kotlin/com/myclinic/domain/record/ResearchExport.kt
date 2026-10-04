package com.myclinic.domain.record

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** What the doctor chose on the research export screen. */
data class ResearchExportOptions(
    /** Off: dates become year-month ("2026-03"). Intervals in days are always kept. */
    val fullDates: Boolean = false,
    /** Off: free-text notes (history, operative notes, reports) are left out; they may name people. */
    val freeText: Boolean = false,
)

/** One spreadsheet of the export (opened in Excel / Google Sheets). */
data class CsvFile(val name: String, val text: String)

/**
 * A de-identified research / audit dataset built on the phone from the
 * doctor's own patients. Nothing that identifies the patient leaves:
 *  - no name, national ID, file number, phone, address, emergency contact
 *    or date of birth; age is given in years (90 and over as "90+");
 *  - each patient gets a random study number (P001, P002...) in random
 *    order, and no key back to the patient is kept;
 *  - dates are reduced to year-month unless the doctor asks for full dates;
 *  - free-text notes are left out unless the doctor asks for them.
 * Several spreadsheets are linked by the study number.
 */
object ResearchExport {

    fun build(
        records: List<PatientRecord>,
        options: ResearchExportOptions,
        today: LocalDate,
        random: Random = Random.Default,
    ): List<CsvFile> {
        val ordered = records.shuffled(random)
        val ids = ordered.mapIndexed { i, r -> r.patient.id to studyId(i + 1, ordered.size) }.toMap()
        fun date(value: String?): String = Dates.localDate(value)?.let { if (options.fullDates) it.toString() else it.toString().take(7) }.orEmpty()
        fun free(value: String?): String = if (options.freeText) value.orEmpty() else ""

        val patients = Csv(
            "study_id", "age_years", "sex", "status", "primary_diagnosis", "tags",
            "medical_conditions", "past_surgery", "current_medications", "allergies", "family_history",
            "smoking", "pack_years", "operations", "first_recorded",
        )
        val operations = Csv(
            "study_id", "operation_no", "diagnosis", "operation", "status", "operation_date", "age_at_operation",
            "complications", "follow_up_visits", "operative_notes",
        )
        val followups = Csv("study_id", "operation_no", "visit_date", "days_after_operation", "wound_status", "notes")
        val labs = Csv("study_id", "result_date", "investigation", "test", "value", "unit", "low", "high", "abnormal")
        val reports = Csv("study_id", "result_date", "kind", "investigation", "report")

        for (r in ordered) {
            val sid = ids.getValue(r.patient.id)
            val p = r.patient
            val social = r.socialHistory
            patients.row(
                sid, age(Dates.age(p.dateOfBirth, p.ageYears, today)), p.sex,
                if (p.isDischarged) "discharged" else "active",
                p.primaryDiagnosis, p.tags.joinToString("; "),
                r.conditions.joinToString("; ") { it.name },
                r.surgicalHistory.joinToString("; ") { listOf(it.procedure, date(it.performedOn)).filter(String::isNotEmpty).joinToString(" ") },
                r.medications.filter { it.isCurrent }.joinToString("; ") { listOfNotNull(it.name, it.dose).joinToString(" ") },
                r.allergies.joinToString("; ") { "${it.allergen} (${it.severity})" },
                r.familyHistory.joinToString("; ") { "${it.relation}: ${it.condition}" },
                social?.smoking, social?.packYears?.let(::number), r.surgicalCases.size.toString(), date(p.createdAt),
            )
            val cases = r.surgicalCases.sortedBy { Dates.sortKey(it.operationDate ?: it.plannedDate ?: it.createdAt) }
            cases.forEachIndexed { i, c ->
                val opDate = Dates.localDate(c.operationDate)
                val visits = r.followups.filter { it.surgicalCaseId == c.id }.sortedBy { Dates.sortKey(it.visitDate) }
                operations.row(
                    sid, (i + 1).toString(), c.diagnosis, c.plannedOperation, c.status, date(c.operationDate),
                    opDate?.let { age(ageOn(p, it, today)) },
                    c.complications, visits.size.toString(), free(c.operativeNotes),
                )
                visits.forEach { v ->
                    val day = Dates.localDate(v.visitDate)
                    followups.row(
                        sid, (i + 1).toString(), date(v.visitDate),
                        if (opDate != null && day != null) ChronoUnit.DAYS.between(opDate, day).toString() else "",
                        v.woundStatus, free(v.notes),
                    )
                }
            }
            r.investigationResults.sortedBy { Dates.sortKey(it.resultDate) }.forEach { res ->
                res.labValues.forEach { v ->
                    labs.row(
                        sid, date(res.resultDate), res.title, v.test, v.value?.let(::number) ?: v.text, v.unit,
                        v.low?.let(::number), v.high?.let(::number), if (v.isAbnormal) "yes" else "",
                    )
                }
                if (options.freeText && !res.reportText.isNullOrBlank()) {
                    reports.row(sid, date(res.resultDate), res.kind, res.title, res.reportText)
                }
            }
        }
        return buildList {
            add(CsvFile("patients.csv", patients.text()))
            add(CsvFile("operations.csv", operations.text()))
            add(CsvFile("follow_ups.csv", followups.text()))
            add(CsvFile("lab_results.csv", labs.text()))
            if (options.freeText) add(CsvFile("reports.csv", reports.text()))
            add(CsvFile("README.txt", readme(ordered.size, options, today)))
        }
    }

    /** P001... wide enough for the number of patients. */
    private fun studyId(n: Int, total: Int): String = "P" + n.toString().padStart(maxOf(3, total.toString().length), '0')

    /** Ages over 89 are grouped, as rare high ages can identify someone. */
    private fun age(years: Int?): String = when {
        years == null -> ""
        years >= 90 -> "90+"
        else -> years.toString()
    }

    /** Age on [day]: from the date of birth, or from the age recorded today minus the years since. */
    private fun ageOn(p: Patient, day: LocalDate, today: LocalDate): Int? =
        Dates.age(p.dateOfBirth, null, day)
            ?: p.ageYears?.let { it - ChronoUnit.YEARS.between(day, today).toInt().coerceAtLeast(0) }

    private fun number(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

    private fun readme(count: Int, options: ResearchExportOptions, today: LocalDate) = """
        My Clinic: de-identified research / audit export
        Exported: $today. Patients: $count.

        Files are linked by study_id. Study numbers are random and there is no key back to the patients.
        Removed: names, national ID, file number, phone, address, emergency contact, date of birth.
        Ages are in years (90 and over shown as 90+).
        Dates: ${if (options.fullDates) "full dates (YYYY-MM-DD)" else "year and month only (YYYY-MM)"}.
        Free-text notes: ${if (options.freeText) "included - check them for names before sharing" else "left out"}.

        Research on patient data usually needs approval from your ethics committee (IRB) and must follow
        your local data-protection law. Keep this file encrypted and delete it when no longer needed.
    """.trimIndent() + "\n"

    /** A small CSV writer: RFC 4180 quoting, and no cell can start a spreadsheet formula. */
    internal class Csv(vararg val header: String) {
        private val rows = mutableListOf<List<String>>(header.toList())

        fun row(vararg cells: String?) {
            require(cells.size == header.size) { "expected ${header.size} cells, got ${cells.size}" }
            rows += cells.map { it.orEmpty() }
        }

        fun text(): String = rows.joinToString("\r\n", postfix = "\r\n") { r -> r.joinToString(",") { cell(it) } }

        private fun cell(raw: String): String {
            val formula = (raw.isNotEmpty() && raw[0] in "=+@\t\r") || (raw.startsWith("-") && raw.toDoubleOrNull() == null)
            val safe = if (formula) "'$raw" else raw
            return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
        }
    }
}
