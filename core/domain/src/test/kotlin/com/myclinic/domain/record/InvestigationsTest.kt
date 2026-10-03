package com.myclinic.domain.record

import com.myclinic.domain.TestData
import com.myclinic.domain.forms.FieldError
import com.myclinic.domain.forms.FormCodec
import com.myclinic.domain.forms.FormSpecs
import com.myclinic.domain.model.AccountType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Investigations: requests, typed results, files and what lab staff see. All demo data. */
class InvestigationsTest {
    private val json = DemoRecords::json
    private val today = LocalDate.of(2026, 10, 3)

    private val record = PatientRecordAssembler.assemble(
        DemoRecords.patientJson,
        DemoRecords.rows + listOf(
            CachedRow(RecordTable.INVESTIGATION_REQUESTS, json("""{"id":"r1","patient_id":"p1","kind":"lab","tests":["CBC"],"urgency":"urgent","facility_id":"f1","status":"result_uploaded","requested_at":"2026-10-01T08:00:00+00:00","resulted_at":"2026-10-01T12:00:00+00:00"}""")),
            CachedRow(RecordTable.INVESTIGATION_REQUESTS, json("""{"id":"r2","patient_id":"p1","kind":"imaging","tests":["Chest X-ray"],"status":"requested","requested_at":"2026-10-02T08:00:00+00:00"}""")),
            CachedRow(RecordTable.INVESTIGATION_RESULTS, json("""{"id":"x1","patient_id":"p1","request_id":"r1","kind":"lab","title":"CBC","result_date":"2026-10-01","source":"staff","lab_values":[{"test":"Hb","value":11.2,"unit":"g/dL","low":13,"high":17},{"test":"WBC","value":7.1}]}""")),
            CachedRow(RecordTable.INVESTIGATION_RESULTS, json("""{"id":"x2","patient_id":"p1","kind":"lab","title":"CBC (old)","result_date":"2026-09-01","lab_values":[{"test":"hb","value":13.5,"low":13,"high":17}]}""")),
            CachedRow(RecordTable.INVESTIGATION_RESULTS, json("""{"id":"x3","patient_id":"p1","kind":"lab","title":"Entered in error","result_date":"2026-09-15","deleted_at":"2026-09-16T00:00:00+00:00","lab_values":[{"test":"Hb","value":1}]}""")),
            CachedRow(RecordTable.ATTACHMENTS, json("""{"id":"at1","patient_id":"p1","section":"investigations","result_id":"x1","storage_path":"investigations/p1/cbc.pdf","mime_type":"application/pdf"}""")),
        ),
    )!!

    @Test
    fun `requests, results and files are read from the cache`() {
        assertEquals(listOf("r2", "r1"), record.investigationRequests.map { it.id })
        assertEquals(listOf("x1", "x2"), record.investigationResults.map { it.id }) // newest first, deleted left out
        assertEquals(listOf("x1"), record.resultsFor(record.investigationRequests[1]).map { it.id })
        assertTrue(record.attachmentsForResult("x1").single().isPdf)
        assertTrue(record.investigationResults[0].labValues[0].isAbnormal)
        assertFalse(record.investigationResults[0].labValues[1].isAbnormal)
    }

    @Test
    fun `department results cannot be edited and request actions follow the status`() {
        val (fromLab, ownResult) = record.investigationResults
        assertFalse(InvestigationRules.canEditResult(fromLab))
        assertTrue(InvestigationRules.canEditResult(ownResult))

        val (pending, ready) = record.investigationRequests
        assertTrue(InvestigationRules.canCancel(pending))
        assertFalse(InvestigationRules.canReview(pending))
        assertTrue(InvestigationRules.canReview(ready))
        assertFalse(InvestigationRules.canCancel(ready))
        assertFalse("sent to the lab and already resulted", InvestigationRules.canEditRequest(ready))
        assertFalse(InvestigationRules.canAddResult(ready.copy(status = InvestigationStatus.REVIEWED)))
    }

    @Test
    fun `home screen counts pending and ready results`() {
        assertEquals(InvestigationCounts(awaitingResult = 1, awaitingReview = 1), InvestigationDashboard.counts(listOf(record)))
        assertEquals(listOf("r1"), InvestigationDashboard.toReview(listOf(record)).map { it.request.id })
        val deleted = record.copy(patient = record.patient.copy(deletedAt = "2026-10-02T00:00:00Z"))
        assertEquals(InvestigationCounts(0, 0), InvestigationDashboard.counts(listOf(deleted)))
    }

    @Test
    fun `lab trends match test names loosely and ignore deleted results`() {
        assertEquals(listOf("Hb", "WBC"), LabTrends.available(record.investigationResults))
        val hb = LabTrends.series(record.investigationResults, "HB")
        assertEquals(listOf(13.5, 11.2), hb.map { it.value })
        assertEquals(listOf(false, true), hb.map { it.abnormal })
    }

    @Test
    fun `timeline shows requests and results`() {
        val events = TimelineBuilder.build(record)
        val result = events.first { it.kind == TimelineKind.INVESTIGATION_RESULT && it.recordId == "x1" }
        assertEquals("Hb 11.2 g/dL ↓ · WBC 7.1", result.detail)
        assertTrue(events.any { it.kind == TimelineKind.INVESTIGATION_REQUESTED && it.title == "Chest X-ray" })
    }

    @Test
    fun `a CBC request starts the result with its tests, units and ranges`() {
        val values = LabCatalog.startingValues(listOf("CBC", "Hb", "Vitamin D"))
        assertEquals(listOf("Hb", "WBC", "Platelets", "Haematocrit", "Vitamin D"), values.map { it.test })
        assertEquals("g/dL", values[0].unit)
        assertNull(values.last().unit)
        assertEquals("Creatinine", LabCatalog.find(" creatinine ")?.name)
    }

    @Test
    fun `typed values round-trip through the form`() {
        val spec = FormSpecs.INVESTIGATION_RESULT
        val values = FormCodec.newValues(spec, today = today) + mapOf(
            "title" to "CBC",
            "lab_values" to FormCodec.labValuesText(listOf(LabValue("Hb", 11.2, unit = "g/dL", low = 13.0, high = 17.0), LabValue(" ", 1.0))),
        )
        assertTrue(FormCodec.validate(spec, values, today).isEmpty())
        val row = FormCodec.toJson(spec, values, JsonObject(mapOf("id" to JsonPrimitive("x"))))
        val list = row["lab_values"] as JsonArray
        assertEquals(1, list.size) // the row without a test name is dropped
        assertEquals(listOf(LabValue("Hb", 11.2, unit = "g/dL", low = 13.0, high = 17.0)),
            FormCodec.parseLabValues(FormCodec.fromJson(spec, row).getValue("lab_values")))
        // No values: an empty list, never null (the column requires a list).
        assertEquals(JsonArray(emptyList()), FormCodec.toJson(spec, values + ("lab_values" to ""), JsonObject(emptyMap()))["lab_values"])
    }

    @Test
    fun `typed values are validated`() {
        val spec = FormSpecs.INVESTIGATION_RESULT
        val base = FormCodec.newValues(spec, today = today) + ("title" to "CBC")
        fun error(values: List<LabValue>) = FormCodec.validate(spec, base + ("lab_values" to FormCodec.labValuesText(values)), today)["lab_values"]
        assertEquals(FieldError.REQUIRED, error(listOf(LabValue("Hb"))))
        assertNull(error(listOf(LabValue("Urine protein", text = "trace"))))
        assertEquals(FieldError.OUT_OF_RANGE, error(listOf(LabValue("Hb", 12.0, low = 17.0, high = 13.0))))
        assertEquals(FieldError.NOT_A_NUMBER, FormCodec.validate(spec, base + ("lab_values" to "{oops"), today)["lab_values"])
        assertEquals(FieldError.DATE_IN_FUTURE,
            FormCodec.validate(spec, base + ("result_date" to "2026-10-04"), today)["result_date"])
    }

    @Test
    fun `a request needs at least one test`() {
        val spec = FormSpecs.INVESTIGATION_REQUEST
        val values = FormCodec.newValues(spec, today = today)
        assertEquals(FieldError.REQUIRED, FormCodec.validate(spec, values, today)["tests"])
        val ok = values + ("tests" to "CBC,Creatinine")
        assertTrue(FormCodec.validate(spec, ok, today).isEmpty())
        val row = FormCodec.toJson(spec, ok, JsonObject(emptyMap()))
        assertEquals(JsonArray(listOf(JsonPrimitive("CBC"), JsonPrimitive("Creatinine"))), row["tests"])
        assertEquals("handled by the doctor", kotlinx.serialization.json.JsonNull as Any, row["facility_id"])
    }

    @Test
    fun `staff profiles need a department`() {
        val staff = TestData.doctor("d1").copy(accountType = AccountType.STAFF, facilityId = null)
        assertFalse(staff.isProfileComplete)
        assertTrue(staff.copy(facilityId = "f1").isProfileComplete)
        assertTrue(TestData.doctor("d1").isProfileComplete)
    }

    @Test
    fun `departments match the kind of request and duplicates are detected`() {
        val lab = Facility("f1", "Main Lab", "lab", "Demo Hospital")
        assertEquals("Main Lab – Demo Hospital", lab.label)
        assertTrue(FacilityValidator.isDuplicate(listOf(lab), " main lab ", "lab", "demo hospital "))
        assertFalse(FacilityValidator.isDuplicate(listOf(lab), "Main Lab", "radiology", "Demo Hospital"))
        assertTrue(FacilityValidator.accepts("radiology", "imaging"))
        assertFalse(FacilityValidator.accepts("lab", "imaging"))
        assertFalse(FacilityValidator.isValidName(" x "))
    }

    @Test
    fun `what lab staff receive from the server is read`() {
        val detail = RecordJson.decodeFromString(
            StaffRequestDetail.serializer(),
            """{"id":"r1","patient_id":"p1","kind":"lab","tests":["CBC"],"urgency":"stat","status":"requested",
               "clinical_notes":null,"requested_at":"2026-10-01T08:00:00+00:00","requested_by":"Dr Demo",
               "patient":{"full_name":"Demo Patient","sex":"male","file_number":"F-1","age_years":54},
               "allergies":[{"allergen":"Iodine (demo)","severity":"severe","reaction":null}],
               "previous_results":[{"title":"CBC","result_date":"2026-09-01","report_text":null,"lab_values":[{"test":"Hb","value":10.1}]}],
               "this_request_results":[]}""",
        )
        assertEquals("Demo Patient", detail.patient.fullName)
        assertEquals(10.1, detail.previousResults.single().labValues.single().value!!, 0.0)
        val item = RecordJson.decodeFromString(
            WorklistItem.serializer(),
            """{"id":"r1","patient_id":"p1","kind":"lab","tests":["CBC"],"urgency":"stat","status":"requested",
               "requested_at":"2026-10-01T08:00:00+00:00","patient_name":"Demo Patient","patient_sex":"male",
               "file_number":null,"age_years":54,"requested_by":"Dr Demo"}""",
        )
        assertEquals(54, item.ageYears)
    }
}
