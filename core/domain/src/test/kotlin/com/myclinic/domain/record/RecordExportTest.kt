package com.myclinic.domain.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecordExportTest {
    private val labels = ExportLabels(
        ageYears = { "$it y" }, noKnownAllergies = "No known allergies", current = "current", stopped = "stopped",
        investigations = "Investigations", checklist = { d, t -> "$d/$t" },
    )

    private fun build(record: PatientRecord, lang: String = "en") =
        RecordExport.build(record, lang, LocalDate.of(2026, 10, 6), { it }, { it }, labels)

    @Test
    fun `the printable record has the patient, allergies first, and leaves out deleted entries`() {
        val doc = build(DemoRecords.record)
        assertEquals("أحمد التجريبي", doc.title)
        assertTrue(doc.subtitle.first().startsWith("46 y · Male"))
        val headings = doc.sections.map { it.heading }
        assertEquals(listOf("Personal data", "Allergies"), headings.take(2))
        val allergies = doc.sections[1].lines
        assertEquals("Demo penicillin · Life-threatening", allergies.first())
        assertFalse(allergies.any { "Entered in error" in it })
        assertTrue(doc.sections.any { s -> s.lines.any { "Laparoscopic appendicectomy" in it && "1/10" in it } })
    }

    @Test
    fun `empty sections are left out, but a missing allergy list says so`() {
        val bare = PatientRecord(Patient(id = "p", ownerId = "d", fullName = "Demo Bare"))
        val doc = build(bare)
        assertEquals(listOf("Allergies"), doc.sections.map { it.heading })
        assertEquals(listOf("No known allergies"), doc.sections.single().lines)
    }

    @Test
    fun `arabic labels are used for an arabic export`() {
        val doc = build(DemoRecords.record, "ar")
        assertTrue(doc.sections.any { it.heading == "الحساسية" })
    }
}
