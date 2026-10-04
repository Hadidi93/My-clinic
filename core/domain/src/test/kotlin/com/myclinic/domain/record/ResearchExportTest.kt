package com.myclinic.domain.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/** The research export must never carry anything that identifies a patient. Demo data only. */
class ResearchExportTest {

    private val today = LocalDate.of(2026, 10, 8)

    private val record = PatientRecord(
        Patient(
            id = "p1", ownerId = "d", fullName = "Demo Patient Alpha", dateOfBirth = "1970-04-20", sex = "female",
            nationalId = "29001010101010", fileNumber = "DEMO-77", phone = "+201000000009", address = "1 Demo Street",
            emergencyContactName = "Demo Relative", emergencyContactPhone = "+201000000010",
            primaryDiagnosis = "Gallstones, symptomatic", tags = listOf("post-op"), createdAt = "2026-03-02T09:00:00Z",
            dischargedAt = "2026-06-01T09:00:00Z",
        ),
        conditions = listOf(MedicalCondition(id = "c1", patientId = "p1", name = "Type 2 diabetes")),
        medications = listOf(
            Medication(id = "m1", patientId = "p1", name = "Metformin", dose = "500 mg"),
            Medication(id = "m2", patientId = "p1", name = "Old drug", isCurrent = false),
        ),
        surgicalCases = listOf(
            SurgicalCase(id = "s1", patientId = "p1", diagnosis = "Gallstones", plannedOperation = "Lap chole", status = "completed",
                operationDate = "2026-03-15", complications = "None", operativeNotes = "Demo note naming Dr Demo"),
        ),
        followups = listOf(PostopFollowup(id = "f1", patientId = "p1", surgicalCaseId = "s1", visitDate = "2026-03-25",
            woundStatus = "healed", notes = "Seen with husband Demo")),
        investigationResults = listOf(
            InvestigationResult(id = "r1", patientId = "p1", title = "CBC", resultDate = "2026-03-10",
                reportText = "Demo report", labValues = listOf(LabValue("Hb", 9.5, unit = "g/dL", low = 12.0, high = 16.0))),
        ),
    )
    private val old = PatientRecord(Patient(id = "p2", ownerId = "d", fullName = "Demo Old", ageYears = 93, sex = "male"))

    private fun export(options: ResearchExportOptions = ResearchExportOptions()) =
        ResearchExport.build(listOf(record, old), options, today, Random(1)).associate { it.name to it.text }

    @Test
    fun `no identifying detail appears in any file`() {
        val all = export(ResearchExportOptions(fullDates = true, freeText = true)).values.joinToString("\n")
        listOf("Demo Patient Alpha", "29001010101010", "DEMO-77", "+201000000009", "1 Demo Street", "Demo Relative",
            "+201000000010", "1970-04-20", "p1", "Demo Old").forEach { assertFalse("leaked: $it", it in all) }
    }

    @Test
    fun `patients get study numbers, age in years and 90 plus for the very old`() {
        val rows = export().getValue("patients.csv").trim().lines()
        assertEquals(3, rows.size)
        assertTrue(rows[0].startsWith("study_id,age_years,sex,status"))
        val alpha = rows.first { "female" in it }
        assertTrue(alpha.startsWith("P00"))
        assertTrue(",56,female,discharged," in alpha)
        assertTrue("Metformin 500 mg" in alpha)
        assertFalse("stopped drugs are not current medications", "Old drug" in alpha)
        assertTrue(rows.any { ",90+,male,active," in it })
    }

    @Test
    fun `dates are year-month by default, intervals and free text follow the options`() {
        val files = export()
        assertTrue("age at the operation, not today", "completed,2026-03,55," in files.getValue("operations.csv"))
        assertFalse("2026-03-15" in files.values.joinToString())
        assertTrue(",2026-03,10,healed," in files.getValue("follow_ups.csv"))
        assertFalse("Demo note" in files.values.joinToString() || "husband" in files.values.joinToString())
        assertFalse("reports.csv" in files)
        assertTrue("CBC,Hb,9.5,g/dL,12,16,yes" in files.getValue("lab_results.csv"))

        val full = export(ResearchExportOptions(fullDates = true, freeText = true))
        assertTrue("2026-03-15" in full.getValue("operations.csv"))
        assertTrue("Demo report" in full.getValue("reports.csv"))
    }

    @Test
    fun `cells are quoted and can't run spreadsheet formulas`() {
        val csv = ResearchExport.Csv("a", "b", "c")
        csv.row("x, y", "=HYPERLINK(\"bad\")", "-5")
        assertEquals("a,b,c\r\n\"x, y\",\"'=HYPERLINK(\"\"bad\"\")\",-5\r\n", csv.text())
    }
}
