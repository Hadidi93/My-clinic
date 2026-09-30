package com.myclinic.domain.forms

import com.myclinic.domain.record.RecordTable

/** How a field is edited on screen. */
enum class FieldType {
    TEXT, MULTILINE, PHONE,
    INT, DECIMAL,
    DATE, DATETIME,
    BOOLEAN,
    CHOICE,         // one of [FieldSpec.options]
    CHECKLIST,      // several of [FieldSpec.options], stored as {"item": true}
    TAGS,           // list of short labels
    CONDITION,      // chronic-disease list or free text; fills name + condition_code
    SURGICAL_CASE,  // picks one of the patient's surgical cases
}

/**
 * One field on a record form. [key] is the database column. Limits mirror
 * the CHECK constraints in the migrations, so the app rejects bad input
 * before the server would.
 */
data class FieldSpec(
    val key: String,
    val type: FieldType,
    val required: Boolean = false,
    val maxLength: Int? = null,
    val min: Double? = null,
    val max: Double? = null,
    val options: List<String> = emptyList(),
    /** Default for a new entry: "now" / "today" for dates, otherwise the literal value. */
    val default: String? = null,
    /** Show on the quick-add form. */
    val quick: Boolean = false,
)

data class FormSpec(val table: RecordTable, val fields: List<FieldSpec>) {
    fun field(key: String): FieldSpec? = fields.firstOrNull { it.key == key }
}

object PreopChecklist {
    val ITEMS = listOf(
        "consent_signed", "npo_confirmed", "labs_reviewed", "blood_crossmatched",
        "anaesthesia_review", "site_marked", "antibiotic_prophylaxis", "dvt_prophylaxis",
        "imaging_available", "anticoagulants_reviewed",
    )
}

object FormSpecs {
    val PATIENT = FormSpec(
        RecordTable.PATIENTS,
        listOf(
            FieldSpec("full_name", FieldType.TEXT, required = true, maxLength = 120, quick = true),
            FieldSpec("sex", FieldType.CHOICE, required = true, options = listOf("male", "female", "unknown"),
                default = "unknown", quick = true),
            FieldSpec("age_years", FieldType.INT, min = 0.0, max = 130.0, quick = true),
            FieldSpec("date_of_birth", FieldType.DATE),
            FieldSpec("file_number", FieldType.TEXT, maxLength = 40, quick = true),
            FieldSpec("national_id", FieldType.TEXT, maxLength = 30),
            FieldSpec("phone", FieldType.PHONE, maxLength = 20),
            FieldSpec("address", FieldType.MULTILINE, maxLength = 300),
            FieldSpec("emergency_contact_name", FieldType.TEXT, maxLength = 120),
            FieldSpec("emergency_contact_phone", FieldType.PHONE, maxLength = 20),
            FieldSpec("primary_diagnosis", FieldType.TEXT, maxLength = 200, quick = true),
            FieldSpec("tags", FieldType.TAGS, quick = true),
        ),
    )

    val COMPLAINT = FormSpec(
        RecordTable.PRESENTING_COMPLAINTS,
        listOf(
            FieldSpec("complaint", FieldType.TEXT, required = true, maxLength = 500),
            FieldSpec("hpi", FieldType.MULTILINE, maxLength = 10000),
            FieldSpec("onset_date", FieldType.DATE),
            FieldSpec("recorded_at", FieldType.DATETIME, required = true, default = "now"),
        ),
    )

    val CONDITION = FormSpec(
        RecordTable.MEDICAL_CONDITIONS,
        listOf(
            FieldSpec("name", FieldType.CONDITION, required = true, maxLength = 200),
            FieldSpec("is_chronic", FieldType.BOOLEAN, default = "true"),
            FieldSpec("diagnosed_on", FieldType.DATE),
            FieldSpec("notes", FieldType.MULTILINE, maxLength = 2000),
        ),
    )

    val SURGICAL_HISTORY = FormSpec(
        RecordTable.SURGICAL_HISTORY,
        listOf(
            FieldSpec("procedure", FieldType.TEXT, required = true, maxLength = 200),
            FieldSpec("performed_on", FieldType.DATE),
            FieldSpec("hospital", FieldType.TEXT, maxLength = 120),
            FieldSpec("complications", FieldType.MULTILINE, maxLength = 2000),
        ),
    )

    val MEDICATION = FormSpec(
        RecordTable.MEDICATIONS,
        listOf(
            FieldSpec("name", FieldType.TEXT, required = true, maxLength = 200),
            FieldSpec("dose", FieldType.TEXT, maxLength = 100),
            FieldSpec("route", FieldType.CHOICE,
                options = listOf("oral", "iv", "im", "sc", "topical", "inhaled", "rectal", "other")),
            FieldSpec("frequency", FieldType.TEXT, maxLength = 100),
            FieldSpec("started_on", FieldType.DATE),
            FieldSpec("stopped_on", FieldType.DATE),
            FieldSpec("is_current", FieldType.BOOLEAN, default = "true"),
            FieldSpec("notes", FieldType.MULTILINE, maxLength = 1000),
        ),
    )

    val ALLERGY = FormSpec(
        RecordTable.ALLERGIES,
        listOf(
            FieldSpec("allergen", FieldType.TEXT, required = true, maxLength = 200),
            FieldSpec("severity", FieldType.CHOICE, required = true,
                options = listOf("mild", "moderate", "severe", "life_threatening"), default = "moderate"),
            FieldSpec("reaction", FieldType.TEXT, maxLength = 500),
        ),
    )

    val FAMILY_HISTORY = FormSpec(
        RecordTable.FAMILY_HISTORY,
        listOf(
            FieldSpec("relation", FieldType.CHOICE, required = true,
                options = listOf("father", "mother", "brother", "sister", "son", "daughter", "grandparent", "other")),
            FieldSpec("condition", FieldType.TEXT, required = true, maxLength = 200),
            FieldSpec("notes", FieldType.MULTILINE, maxLength = 1000),
        ),
    )

    val SOCIAL_HISTORY = FormSpec(
        RecordTable.SOCIAL_HISTORY,
        listOf(
            FieldSpec("smoking", FieldType.CHOICE, required = true,
                options = listOf("never", "former", "current", "unknown"), default = "unknown"),
            FieldSpec("pack_years", FieldType.DECIMAL, min = 0.0, max = 300.0),
            FieldSpec("alcohol", FieldType.TEXT, maxLength = 200),
            FieldSpec("occupation", FieldType.TEXT, maxLength = 120),
            FieldSpec("marital_status", FieldType.CHOICE, options = listOf("single", "married", "divorced", "widowed")),
            FieldSpec("notes", FieldType.MULTILINE, maxLength = 2000),
        ),
    )

    val EXAMINATION = FormSpec(
        RecordTable.EXAMINATIONS,
        listOf(
            FieldSpec("examined_at", FieldType.DATETIME, required = true, default = "now"),
            FieldSpec("systolic_mmhg", FieldType.INT, min = 40.0, max = 300.0),
            FieldSpec("diastolic_mmhg", FieldType.INT, min = 20.0, max = 200.0),
            FieldSpec("pulse_bpm", FieldType.INT, min = 20.0, max = 300.0),
            FieldSpec("resp_rate", FieldType.INT, min = 4.0, max = 80.0),
            FieldSpec("temperature_c", FieldType.DECIMAL, min = 30.0, max = 45.0),
            FieldSpec("spo2_percent", FieldType.INT, min = 50.0, max = 100.0),
            FieldSpec("pain_score", FieldType.INT, min = 0.0, max = 10.0),
            FieldSpec("weight_kg", FieldType.DECIMAL, min = 0.3, max = 400.0),
            FieldSpec("height_cm", FieldType.DECIMAL, min = 20.0, max = 250.0),
            FieldSpec("findings", FieldType.MULTILINE, maxLength = 10000),
        ),
    )

    val SURGICAL_CASE = FormSpec(
        RecordTable.SURGICAL_CASES,
        listOf(
            FieldSpec("diagnosis", FieldType.TEXT, required = true, maxLength = 300),
            FieldSpec("planned_operation", FieldType.TEXT, maxLength = 300),
            FieldSpec("status", FieldType.CHOICE, required = true,
                options = listOf("planned", "scheduled", "done", "cancelled"), default = "planned"),
            FieldSpec("planned_date", FieldType.DATE),
            FieldSpec("preop_checklist", FieldType.CHECKLIST, options = PreopChecklist.ITEMS),
            FieldSpec("operation_date", FieldType.DATE),
            FieldSpec("operative_notes", FieldType.MULTILINE, maxLength = 20000),
            FieldSpec("complications", FieldType.MULTILINE, maxLength = 2000),
        ),
    )

    val FOLLOWUP = FormSpec(
        RecordTable.POSTOP_FOLLOWUPS,
        listOf(
            FieldSpec("surgical_case_id", FieldType.SURGICAL_CASE, required = true),
            FieldSpec("visit_date", FieldType.DATE, required = true, default = "today"),
            FieldSpec("wound_status", FieldType.CHOICE,
                options = listOf("healing_well", "erythema", "discharge", "infected", "dehiscence", "healed")),
            FieldSpec("notes", FieldType.MULTILINE, maxLength = 5000),
        ),
    )

    val ALL: List<FormSpec> = listOf(
        PATIENT, COMPLAINT, CONDITION, SURGICAL_HISTORY, MEDICATION, ALLERGY,
        FAMILY_HISTORY, SOCIAL_HISTORY, EXAMINATION, SURGICAL_CASE, FOLLOWUP,
    )

    fun forTable(table: RecordTable): FormSpec = ALL.first { it.table == table }
}
