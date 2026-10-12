package com.myclinic.domain.record

/**
 * Warns before the same thing is entered twice in a history list (the same
 * drug, operation, condition, allergy...). Only a warning: the doctor may
 * still save, e.g. a second operation of the same kind on another date.
 * Entries marked as entered in error don't count.
 */
object DuplicateCheck {

    /** The existing entry that [values] would duplicate, or null. [excludeId] is the entry being edited. */
    fun find(record: PatientRecord, table: RecordTable, values: Map<String, String>, excludeId: String? = null): RecordEntry? {
        fun v(key: String) = values[key]
        fun <T : RecordEntry> List<T>.match(same: (T) -> Boolean): T? =
            firstOrNull { it.id != excludeId && !it.isDeleted && same(it) }
        return when (table) {
            RecordTable.PRESENTING_COMPLAINTS -> v("complaint")?.let { c -> record.complaints.match { same(it.complaint, c) } }
            RecordTable.MEDICAL_CONDITIONS -> v("name")?.let { n ->
                val code = v("condition_code")?.takeIf { it.isNotBlank() }
                record.conditions.match { (code != null && it.conditionCode == code) || same(it.name, n) }
            }
            RecordTable.SURGICAL_HISTORY -> v("procedure")?.let { p ->
                val date = v("performed_on")?.takeIf { it.isNotBlank() }
                // The same operation on two different known dates is two operations.
                record.surgicalHistory.match { same(it.procedure, p) && (date == null || it.performedOn == null || it.performedOn == date) }
            }
            RecordTable.MEDICATIONS -> v("name")?.let { n -> record.medications.match { same(it.name, n) } }
            RecordTable.ALLERGIES -> v("allergen")?.let { a -> record.allergies.match { same(it.allergen, a) } }
            RecordTable.FAMILY_HISTORY -> v("condition")?.let { c ->
                record.familyHistory.match { it.relation == v("relation") && same(it.condition, c) }
            }
            else -> null
        }
    }

    /** Ignores case, spaces and punctuation: "Metformin " = "metformin", "Type 2 DM" = "type-2 dm". */
    fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    private fun same(a: String?, b: String?): Boolean {
        val x = a?.let(::normalize).orEmpty()
        return x.isNotEmpty() && x == b?.let(::normalize)
    }
}
