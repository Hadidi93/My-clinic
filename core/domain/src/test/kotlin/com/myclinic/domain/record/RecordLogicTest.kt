package com.myclinic.domain.record

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecordLogicTest {
    private val record = DemoRecords.record
    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun `server rows are read with snake_case names and unknown columns ignored`() {
        assertEquals("DEMO-001", record.patient.fileNumber)
        assertEquals(listOf("awaiting surgery"), record.patient.tags)
        assertEquals(110, record.examinations.first().pulseBpm)
        assertEquals(97, record.examinations.first().spo2Percent)
        assertEquals(mapOf("consent_signed" to true, "npo_confirmed" to false), record.surgicalCases.single().preopChecklist)
    }

    @Test
    fun `deleted and unreadable entries are left out`() {
        assertEquals(listOf("a1", "a2"), record.allergies.map { it.id })
        assertEquals(listOf("m1"), record.medications.map { it.id })
    }

    @Test
    fun `allergies are ordered most dangerous first for the red banner`() {
        assertEquals("life_threatening", record.allergies.first().severity)
    }

    @Test
    fun `upload payload drops server-managed columns but keeps explicit nulls`() {
        val upload = DemoRecords.patientJson.forUpload()
        assertFalse("created_at" in upload)
        assertFalse("updated_at" in upload)
        assertTrue("address" in upload) // null is sent so a cleared field clears on the server
    }

    @Test
    fun `encoding a model produces the database column names`() {
        val json = RecordJson.encodeToJsonElement(Examination.serializer(), record.examinations.first()).jsonObject
        assertTrue("spo2_percent" in json && "systolic_mmhg" in json && "patient_id" in json)
    }

    @Test
    fun `timeline is newest first, skips undated history, and includes future operations`() {
        val events = TimelineBuilder.build(record)
        assertEquals(TimelineKind.OPERATION_PLANNED, events.first().kind)
        assertEquals("Laparoscopic appendicectomy", events.first().title)
        assertTrue(events.none { it.title == "Undated demo procedure" })
        assertEquals(events.sortedByDescending { it.sortKey }.map { it.sortKey }, events.map { it.sortKey })
        assertTrue(events.any { it.kind == TimelineKind.PAST_SURGERY && it.title == "Demo hernia repair" })
        assertTrue(events.any { it.kind == TimelineKind.MEDICATION_STARTED })
        assertEquals(TimelineKind.PAST_SURGERY, events.last().kind) // 2015 hernia repair is the oldest event
    }

    @Test
    fun `vitals series are oldest first and flag abnormal values`() {
        val pulse = VitalsSeries.series(record.examinations, VitalSign.PULSE)
        assertEquals(listOf(88.0, 110.0), pulse.map { it.value })
        assertEquals(listOf(false, true), pulse.map { it.abnormal })
        assertTrue(VitalSign.TEMPERATURE in VitalsSeries.available(record.examinations))
        assertFalse(VitalSign.WEIGHT in VitalsSeries.available(record.examinations))
    }

    @Test
    fun `vitals summary text`() {
        assertEquals("BP 100/60 · HR 110 · T 38.4 · SpO₂ 97%", VitalsFormatter.summary(record.examinations.first()))
    }

    @Test
    fun `age comes from date of birth, else recorded age`() {
        assertEquals(45, Dates.age("1980-10-01", null, today))
        assertEquals(60, Dates.age(null, 60, today))
        assertNull(Dates.age(null, null, today))
    }
}
