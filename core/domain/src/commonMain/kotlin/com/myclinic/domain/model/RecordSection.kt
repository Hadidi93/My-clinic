package com.myclinic.domain.model

/**
 * The parts of a patient record that can be shared, one checkbox each on the
 * "Consult" screen. The [dbValue]s must match the `record_section` enum in
 * supabase/migrations, because the server enforces sharing per section.
 */
enum class RecordSection(val dbValue: String) {
    /** Name, national ID, file number, phone, address, emergency contact. */
    IDENTIFIERS("identifiers"),
    PRESENTING_COMPLAINT("presenting_complaint"),
    PAST_MEDICAL("past_medical"),
    PAST_SURGICAL("past_surgical"),
    MEDICATIONS("medications"),
    ALLERGIES("allergies"),
    FAMILY_HISTORY("family_history"),
    SOCIAL_HISTORY("social_history"),
    EXAMINATION("examination"),
    INVESTIGATIONS("investigations"),
    /** Diagnosis, planned operation, pre-op checklist, operative notes, follow-up, wound photos. */
    SURGICAL_CARE("surgical_care");

    companion object {
        fun fromDb(value: String): RecordSection? = entries.firstOrNull { it.dbValue == value }
    }
}
