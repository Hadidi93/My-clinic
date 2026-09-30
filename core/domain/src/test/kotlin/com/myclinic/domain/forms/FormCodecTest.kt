package com.myclinic.domain.forms

import com.myclinic.domain.record.DemoRecords
import com.myclinic.domain.record.RecordTable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FormCodecTest {
    private val today = LocalDate.of(2026, 9, 30)
    private val base = JsonObject(mapOf("id" to JsonPrimitive("x"), "patient_id" to JsonPrimitive("p1")))

    @Test
    fun `every table has a form, and every field and option has English and Arabic labels`() {
        assertEquals(RecordTable.entries.toSet(), FormSpecs.ALL.map { it.table }.toSet())
        for (spec in FormSpecs.ALL) {
            assertTrue("section label ${spec.table}", Vocabulary.SECTIONS.containsKey(spec.table))
            for (f in spec.fields) {
                assertTrue("label for ${f.key}", Vocabulary.FIELDS.containsKey(f.key))
                f.options.forEach { assertTrue("label for option $it", Vocabulary.OPTIONS.containsKey(it)) }
            }
        }
        (Vocabulary.FIELDS.values + Vocabulary.OPTIONS.values + ChronicDiseases.ALL.map { it.name })
            .forEach { assertTrue(it.en.isNotBlank() && it.ar.isNotBlank()) }
    }

    @Test
    fun `quick add needs only the name`() {
        val values = FormCodec.newValues(FormSpecs.PATIENT, today = today) + ("full_name" to "Demo Quick")
        assertTrue(FormCodec.validate(FormSpecs.PATIENT, values, today).isEmpty())
        assertEquals(FieldError.REQUIRED, FormCodec.validate(FormSpecs.PATIENT, values + ("full_name" to " "), today)["full_name"])
    }

    @Test
    fun `numbers are range-checked like the database, Arabic digits accepted`() {
        val v = FormCodec.newValues(FormSpecs.EXAMINATION, Instant.parse("2026-09-30T08:00:00Z"), today)
        fun err(key: String, value: String) = FormCodec.validate(FormSpecs.EXAMINATION, v + (key to value), today)[key]
        assertEquals(null, err("pulse_bpm", "٨٨"))
        assertEquals(FieldError.OUT_OF_RANGE, err("pulse_bpm", "400"))
        assertEquals(FieldError.NOT_A_NUMBER, err("pulse_bpm", "fast"))
        assertEquals(null, err("temperature_c", "37,5"))
        assertEquals(37.5, FormCodec.parseDecimal("٣٧٫٥")!!, 0.0001)
    }

    @Test
    fun `past-only dates cannot be in the future, planned dates can`() {
        val v = FormCodec.newValues(FormSpecs.SURGICAL_CASE, today = today) + ("diagnosis" to "Demo")
        assertEquals(FieldError.DATE_IN_FUTURE,
            FormCodec.validate(FormSpecs.SURGICAL_CASE, v + ("operation_date" to "2026-10-01"), today)["operation_date"])
        assertEquals(null,
            FormCodec.validate(FormSpecs.SURGICAL_CASE, v + ("planned_date" to "2026-10-01"), today)["planned_date"])
        assertEquals(FieldError.INVALID_DATE,
            FormCodec.validate(FormSpecs.SURGICAL_CASE, v + ("planned_date" to "31/12/2026"), today)["planned_date"])
    }

    @Test
    fun `json round trip keeps checklist, tags, booleans and clears empty fields`() {
        val row = FormCodec.toJson(
            FormSpecs.SURGICAL_CASE,
            mapOf("diagnosis" to "Demo", "status" to "planned", "preop_checklist" to "consent_signed,site_marked",
                  "operative_notes" to ""),
            base,
        )
        assertEquals(JsonObject(mapOf("consent_signed" to JsonPrimitive(true), "site_marked" to JsonPrimitive(true))),
            row["preop_checklist"])
        assertEquals(JsonNull, row["operative_notes"])
        assertEquals(JsonPrimitive("x"), row["id"]) // base columns kept
        assertEquals("consent_signed,site_marked", FormCodec.fromJson(FormSpecs.SURGICAL_CASE, row)["preop_checklist"])

        val patient = FormCodec.toJson(FormSpecs.PATIENT,
            mapOf("full_name" to "Demo", "sex" to "female", "tags" to "post-op, urgent,post-op", "age_years" to "٤٥"), base)
        assertEquals(JsonArray(listOf(JsonPrimitive("post-op"), JsonPrimitive("urgent"))), patient["tags"])
        assertEquals(JsonPrimitive(45), patient["age_years"])
    }

    @Test
    fun `condition picker stores both the code and the name`() {
        val row = FormCodec.toJson(FormSpecs.CONDITION,
            mapOf("name" to "Hypertension", "condition_code" to "hypertension", "is_chronic" to "true"), base)
        assertEquals(JsonPrimitive("hypertension"), row["condition_code"])
        assertEquals(JsonPrimitive(true), row["is_chronic"])
        val free = FormCodec.toJson(FormSpecs.CONDITION, mapOf("name" to "Rare demo syndrome"), base)
        assertEquals(JsonNull, free["condition_code"])
    }

    @Test
    fun `existing row loads into the form`() {
        val values = FormCodec.fromJson(FormSpecs.PATIENT, DemoRecords.patientJson)
        assertEquals("DEMO-001", values["file_number"])
        assertEquals("awaiting surgery", values["tags"])
        assertEquals("", values["address"])
    }
}
