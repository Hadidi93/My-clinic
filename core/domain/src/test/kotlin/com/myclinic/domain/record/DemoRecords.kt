package com.myclinic.domain.record

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Fake demo patients for tests. No real people. */
object DemoRecords {
    fun json(text: String): JsonObject = RecordJson.parseToJsonElement(text).jsonObject

    val patientJson = json(
        """
        {"id":"p1","owner_id":"d1","full_name":"أحمد التجريبي","date_of_birth":"1980-10-01","age_years":null,
         "sex":"male","national_id":"00000000000001","file_number":"DEMO-001","phone":"+201000000001",
         "address":null,"emergency_contact_name":null,"emergency_contact_phone":null,
         "primary_diagnosis":"Acute appendicitis","tags":["awaiting surgery"],
         "created_at":"2026-09-01T08:00:00.123456+00:00","updated_at":"2026-09-01T08:00:00+00:00","deleted_at":null,
         "some_future_column":"ignored"}
        """,
    )

    val rows = listOf(
        CachedRow(RecordTable.ALLERGIES, json("""{"id":"a1","patient_id":"p1","allergen":"Demo penicillin","severity":"life_threatening","created_at":"2026-09-02T09:00:00+00:00","updated_at":"2026-09-02T09:00:00+00:00"}""")),
        CachedRow(RecordTable.ALLERGIES, json("""{"id":"a2","patient_id":"p1","allergen":"Demo latex","severity":"mild","created_at":"2026-09-02T09:05:00+00:00","updated_at":"2026-09-02T09:05:00+00:00"}""")),
        CachedRow(RecordTable.ALLERGIES, json("""{"id":"a3","patient_id":"p1","allergen":"Entered in error","severity":"mild","deleted_at":"2026-09-03T00:00:00+00:00"}""")),
        CachedRow(RecordTable.EXAMINATIONS, json("""{"id":"e1","patient_id":"p1","examined_at":"2026-09-02T10:00:00+00:00","pulse_bpm":110,"systolic_mmhg":100,"diastolic_mmhg":60,"temperature_c":38.4,"spo2_percent":97,"updated_at":"2026-09-02T10:00:00+00:00"}""")),
        CachedRow(RecordTable.EXAMINATIONS, json("""{"id":"e2","patient_id":"p1","examined_at":"2026-09-01T10:00:00+00:00","pulse_bpm":88,"updated_at":"2026-09-01T10:00:00+00:00"}""")),
        CachedRow(RecordTable.SURGICAL_CASES, json("""{"id":"s1","patient_id":"p1","diagnosis":"Acute appendicitis","planned_operation":"Laparoscopic appendicectomy","planned_date":"2026-10-03","status":"scheduled","preop_checklist":{"consent_signed":true,"npo_confirmed":false},"updated_at":"2026-09-02T11:00:00+00:00"}""")),
        CachedRow(RecordTable.SURGICAL_HISTORY, json("""{"id":"h1","patient_id":"p1","procedure":"Demo hernia repair","performed_on":"2015-05-20","updated_at":"2026-09-01T12:00:00+00:00"}""")),
        CachedRow(RecordTable.SURGICAL_HISTORY, json("""{"id":"h2","patient_id":"p1","procedure":"Undated demo procedure"}""")),
        CachedRow(RecordTable.MEDICAL_CONDITIONS, json("""{"id":"c1","patient_id":"p1","condition_code":"diabetes_t2","name":"Type 2 diabetes","is_chronic":true,"diagnosed_on":"2018-01-01","updated_at":"2026-09-01T12:00:00+00:00"}""")),
        CachedRow(RecordTable.MEDICATIONS, json("""{"id":"m1","patient_id":"p1","name":"Demo metformin","is_current":true,"started_on":"2018-01-02"}""")),
        CachedRow(RecordTable.MEDICATIONS, json("""{"id":"bad","patient_id":"p1"}""")), // unreadable: no name
    )

    val record: PatientRecord = PatientRecordAssembler.assemble(patientJson, rows)!!
}
