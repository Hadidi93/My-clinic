package com.myclinic.domain.record

import com.myclinic.domain.validation.normalizeDigits
import java.time.LocalDate

/**
 * One row of the patient list, with what search and filters need.
 * [lastActivity] is the most recent day anything was recorded for the patient.
 */
data class PatientSummary(
    val patient: Patient,
    val ageYears: Int?,
    val lastActivity: LocalDate?,
    val allergies: List<String>,
    /** Diagnoses from all sections (primary diagnosis, conditions, surgical cases), for searching. */
    val diagnoses: List<String>,
)

enum class DateFilter { ALL, TODAY, LAST_7_DAYS, LAST_30_DAYS }

/** Current patients, those marked "discharged / follow-up finished", or both. */
enum class StatusFilter { ACTIVE, DISCHARGED, ALL }

data class PatientQuery(
    val text: String = "",
    val tags: Set<String> = emptySet(),
    val date: DateFilter = DateFilter.ALL,
    val includeDeleted: Boolean = false,
    val status: StatusFilter = StatusFilter.ACTIVE,
)

/** Suggested tags shown as quick chips; doctors can also type their own. */
val SUGGESTED_TAGS = listOf("awaiting surgery", "post-op", "follow-up", "urgent", "inpatient", "outpatient")

object PatientSearch {

    /**
     * Makes text comparable regardless of how it was typed:
     * lower case, Arabic-Indic digits -> 0-9, Arabic diacritics and tatweel
     * removed, and common Arabic spelling variants unified
     * (أ إ آ -> ا, ى -> ي, ة -> ه), so "أحمد" also finds "احمد".
     */
    fun normalize(text: String): String = buildString(text.length) {
        for (ch in text.normalizeDigits().lowercase()) {
            when (ch) {
                in 'ً'..'ْ', 'ـ', 'ٰ' -> Unit // diacritics, tatweel, superscript alef
                'أ', 'إ', 'آ', 'ٱ' -> append('ا')
                'ى' -> append('ي')
                'ة' -> append('ه')
                'ؤ' -> append('و')
                'ئ' -> append('ي')
                else -> append(ch)
            }
        }
    }.trim()

    fun filter(all: List<PatientSummary>, query: PatientQuery, today: LocalDate): List<PatientSummary> {
        // "#post-op" searches the tag "post-op".
        val words = normalize(query.text).split(Regex("\\s+")).map { it.removePrefix("#") }.filter { it.isNotEmpty() }
        val wantedTags = query.tags.map { normalize(it) }.toSet()

        return all.asSequence()
            .filter { query.includeDeleted || !it.patient.isDeleted }
            .filter { s -> matchesStatus(s.patient, query.status, searching = words.isNotEmpty()) }
            .filter { s -> wantedTags.all { tag -> s.patient.tags.any { normalize(it) == tag } } }
            .filter { s -> matchesDate(s.lastActivity, query.date, today) }
            .filter { s ->
                if (words.isEmpty()) return@filter true
                val haystack = searchableText(s)
                words.all { it in haystack }
            }
            .sortedWith(
                compareByDescending<PatientSummary> { it.lastActivity ?: LocalDate.MIN }
                    .thenBy { normalize(it.patient.fullName) },
            )
            .toList()
    }

    private fun searchableText(s: PatientSummary): String = normalize(
        listOfNotNull(
            s.patient.fullName,
            s.patient.nationalId,
            s.patient.fileNumber,
            s.patient.phone,
            s.patient.primaryDiagnosis,
        ).plus(s.diagnoses).plus(s.patient.tags).joinToString(" | "),
    )

    /** Typing a search also finds discharged patients, so nobody gets "lost" from the list. */
    private fun matchesStatus(p: Patient, status: StatusFilter, searching: Boolean): Boolean = when (status) {
        StatusFilter.ALL -> true
        StatusFilter.DISCHARGED -> p.isDischarged
        StatusFilter.ACTIVE -> searching || !p.isDischarged
    }

    private fun matchesDate(day: LocalDate?, filter: DateFilter, today: LocalDate): Boolean = when (filter) {
        DateFilter.ALL -> true
        DateFilter.TODAY -> day == today
        DateFilter.LAST_7_DAYS -> day != null && !day.isBefore(today.minusDays(6))
        DateFilter.LAST_30_DAYS -> day != null && !day.isBefore(today.minusDays(29))
    }

    /** All tags in use, most frequent first, for the filter chips. */
    fun tagsInUse(all: List<PatientSummary>): List<String> =
        all.flatMap { it.patient.tags }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
}

object PatientSummaries {
    fun from(record: PatientRecord, today: LocalDate): PatientSummary {
        val dates = buildList {
            add(record.patient.updatedAt)
            add(record.patient.createdAt)
            record.complaints.forEach { add(it.updatedAt) }
            record.conditions.forEach { add(it.updatedAt) }
            record.surgicalHistory.forEach { add(it.updatedAt) }
            record.medications.forEach { add(it.updatedAt) }
            record.allergies.forEach { add(it.updatedAt) }
            record.familyHistory.forEach { add(it.updatedAt) }
            add(record.socialHistory?.updatedAt)
            record.examinations.forEach { add(it.updatedAt) }
            record.surgicalCases.forEach { add(it.updatedAt) }
            record.followups.forEach { add(it.updatedAt) }
            record.investigationRequests.forEach { add(it.updatedAt) }
            record.investigationResults.forEach { add(it.updatedAt) }
        }
        return PatientSummary(
            patient = record.patient,
            ageYears = Dates.age(record.patient.dateOfBirth, record.patient.ageYears, today),
            lastActivity = dates.mapNotNull { Dates.localDate(it) }.maxOrNull(),
            allergies = record.allergies.map { it.allergen },
            diagnoses = record.conditions.map { it.name } + record.surgicalCases.map { it.diagnosis },
        )
    }
}

/** An operation shown on the home dashboard. */
data class UpcomingOperation(val patient: Patient, val case: SurgicalCase, val date: LocalDate)

object Dashboard {
    /** Patients with anything recorded today. */
    fun todaysPatients(summaries: List<PatientSummary>, today: LocalDate): List<PatientSummary> =
        summaries.filter { !it.patient.isDeleted && it.lastActivity == today }

    /** Planned or scheduled operations from today up to [days] ahead, soonest first. */
    fun upcomingOperations(records: List<PatientRecord>, today: LocalDate, days: Long = 14): List<UpcomingOperation> =
        records.filter { !it.patient.isDeleted }
            .flatMap { r ->
                r.surgicalCases.filter { it.isUpcoming }.mapNotNull { c ->
                    Dates.localDate(c.plannedDate)
                        ?.takeIf { !it.isBefore(today) && !it.isAfter(today.plusDays(days)) }
                        ?.let { UpcomingOperation(r.patient, c, it) }
                }
            }
            .sortedBy { it.date }
}
