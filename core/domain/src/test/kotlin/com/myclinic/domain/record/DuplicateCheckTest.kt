package com.myclinic.domain.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Duplicate warnings in the history lists. Demo data only. */
class DuplicateCheckTest {

    private val record = PatientRecord(
        Patient(id = "p", ownerId = "d", fullName = "Demo Patient"),
        medications = listOf(
            Medication(id = "m1", patientId = "p", name = "Metformin", dose = "500 mg"),
            Medication(id = "m2", patientId = "p", name = "Aspirin", deletedAt = "2026-10-01T00:00:00Z"),
        ),
        conditions = listOf(MedicalCondition(id = "c1", patientId = "p", name = "Type 2 diabetes", conditionCode = "diabetes_t2")),
        surgicalHistory = listOf(SurgicalHistoryItem(id = "s1", patientId = "p", procedure = "Caesarean section", performedOn = "2019-05-01")),
        allergies = listOf(Allergy(id = "a1", patientId = "p", allergen = "بنسلين")),
        familyHistory = listOf(FamilyHistoryItem(id = "f1", patientId = "p", relation = "father", condition = "Hypertension")),
    )

    private fun find(table: RecordTable, vararg values: Pair<String, String>, excludeId: String? = null) =
        DuplicateCheck.find(record, table, values.toMap(), excludeId)?.id

    @Test
    fun `the same drug typed differently is a duplicate`() {
        assertEquals("m1", find(RecordTable.MEDICATIONS, "name" to "  metformin "))
        assertNull(find(RecordTable.MEDICATIONS, "name" to "Metoprolol"))
    }

    @Test
    fun `entries marked as entered in error and the entry being edited don't count`() {
        assertNull(find(RecordTable.MEDICATIONS, "name" to "Aspirin"))
        assertNull(find(RecordTable.MEDICATIONS, "name" to "Metformin", excludeId = "m1"))
    }

    @Test
    fun `conditions match by code or name, allergies in Arabic too`() {
        assertEquals("c1", find(RecordTable.MEDICAL_CONDITIONS, "name" to "DM type 2", "condition_code" to "diabetes_t2"))
        assertEquals("c1", find(RecordTable.MEDICAL_CONDITIONS, "name" to "type 2 Diabetes!"))
        assertEquals("a1", find(RecordTable.ALLERGIES, "allergen" to "بنسلين "))
    }

    @Test
    fun `the same operation on another known date is not a duplicate`() {
        assertEquals("s1", find(RecordTable.SURGICAL_HISTORY, "procedure" to "caesarean section", "performed_on" to ""))
        assertEquals("s1", find(RecordTable.SURGICAL_HISTORY, "procedure" to "Caesarean section", "performed_on" to "2019-05-01"))
        assertNull(find(RecordTable.SURGICAL_HISTORY, "procedure" to "Caesarean section", "performed_on" to "2022-03-10"))
    }

    @Test
    fun `family history needs the same relative and condition`() {
        assertEquals("f1", find(RecordTable.FAMILY_HISTORY, "relation" to "father", "condition" to "hypertension"))
        assertNull(find(RecordTable.FAMILY_HISTORY, "relation" to "mother", "condition" to "hypertension"))
    }

    @Test
    fun `blank values and other tables never warn`() {
        assertNull(find(RecordTable.MEDICATIONS, "name" to " "))
        assertNull(find(RecordTable.EXAMINATIONS, "name" to "Metformin"))
    }
}
