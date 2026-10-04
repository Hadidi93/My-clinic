package com.myclinic.domain.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PatientSearchTest {
    private val today = LocalDate.of(2026, 9, 30)

    private fun summary(id: String, name: String, tags: List<String> = emptyList(), last: LocalDate? = today,
                        diagnoses: List<String> = emptyList(), deleted: Boolean = false, file: String? = null) =
        PatientSummary(
            patient = Patient(id = id, ownerId = "d1", fullName = name, tags = tags, fileNumber = file,
                deletedAt = if (deleted) "2026-09-29T00:00:00Z" else null),
            ageYears = null, lastActivity = last, allergies = emptyList(), diagnoses = diagnoses,
        )

    private val all = listOf(
        summary("1", "أحمد محمد", tags = listOf("post-op"), file = "F-100"),
        summary("2", "Sara Demo", tags = listOf("awaiting surgery"), last = today.minusDays(10), diagnoses = listOf("Cholecystitis")),
        summary("3", "Mona Demo", last = today.minusDays(40)),
        summary("4", "Deleted Demo", deleted = true),
    )

    private fun ids(q: PatientQuery) = PatientSearch.filter(all, q, today).map { it.patient.id }

    @Test
    fun `arabic spelling variants and diacritics match`() {
        assertEquals(listOf("1"), ids(PatientQuery(text = "احمد")))
        assertEquals(listOf("1"), ids(PatientQuery(text = "أَحْمَد")))
        assertEquals("مدرسه", PatientSearch.normalize("مدرسة"))
    }

    @Test
    fun `search by file number and diagnosis, case-insensitive`() {
        assertEquals(listOf("1"), ids(PatientQuery(text = "f-100")))
        assertEquals(listOf("2"), ids(PatientQuery(text = "CHOLE")))
    }

    @Test
    fun `every word must match`() {
        assertEquals(listOf("2"), ids(PatientQuery(text = "sara demo")))
        assertEquals(emptyList<String>(), ids(PatientQuery(text = "sara mona")))
    }

    @Test
    fun `tag and date filters`() {
        assertEquals(listOf("2"), ids(PatientQuery(tags = setOf("Awaiting Surgery"))))
        assertEquals(listOf("1"), ids(PatientQuery(date = DateFilter.TODAY)))
        assertEquals(listOf("1", "2"), ids(PatientQuery(date = DateFilter.LAST_30_DAYS)))
    }

    @Test
    fun `deleted patients are hidden unless asked for, most recent first`() {
        assertEquals(listOf("1", "2", "3"), ids(PatientQuery()))
        assertTrue("4" in ids(PatientQuery(includeDeleted = true)))
    }

    @Test
    fun `discharged patients leave the main list but searching still finds them`() {
        val withDischarged = all + PatientSummary(
            patient = Patient(id = "5", ownerId = "d1", fullName = "Discharged Demo", dischargedAt = "2026-09-01T00:00:00Z"),
            ageYears = null, lastActivity = today, allergies = emptyList(), diagnoses = emptyList(),
        )
        fun ids(q: PatientQuery) = PatientSearch.filter(withDischarged, q, today).map { it.patient.id }
        assertTrue("5" !in ids(PatientQuery()))
        assertEquals(listOf("5"), ids(PatientQuery(status = StatusFilter.DISCHARGED)))
        assertTrue("5" in ids(PatientQuery(status = StatusFilter.ALL)))
        assertEquals(listOf("5"), ids(PatientQuery(text = "discharged")))
    }

    @Test
    fun `dashboard shows today's patients and upcoming operations`() {
        assertEquals(listOf("1"), Dashboard.todaysPatients(all, today).map { it.patient.id })
        val ops = Dashboard.upcomingOperations(listOf(DemoRecords.record), today)
        assertEquals(LocalDate.of(2026, 10, 3), ops.single().date)
        assertEquals(emptyList<UpcomingOperation>(), Dashboard.upcomingOperations(listOf(DemoRecords.record), LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun `summary gathers age, allergies, diagnoses and last activity`() {
        val s = PatientSummaries.from(DemoRecords.record, today)
        assertEquals(45, s.ageYears)
        assertEquals(listOf("Demo penicillin", "Demo latex"), s.allergies)
        assertTrue("Type 2 diabetes" in s.diagnoses && "Acute appendicitis" in s.diagnoses)
        assertEquals(LocalDate.of(2026, 9, 2), s.lastActivity)
    }
}
