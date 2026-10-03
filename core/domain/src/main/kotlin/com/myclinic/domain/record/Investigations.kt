package com.myclinic.domain.record

import kotlinx.serialization.Serializable
import java.time.Instant

/** Compares test names ignoring case, spaces and Arabic spelling variants ("hb" = "Hb", "C R P" = "CRP"). */
internal fun testKey(name: String): String = PatientSearch.normalize(name).filterNot { it.isWhitespace() }

/** Request status values, in the order a request normally moves through them. Mirrors the SQL enum. */
object InvestigationStatus {
    const val REQUESTED = "requested"
    const val SAMPLE_TAKEN = "sample_taken"
    const val RESULT_UPLOADED = "result_uploaded"
    const val REVIEWED = "reviewed"
    const val CANCELLED = "cancelled"

    val ALL = listOf(REQUESTED, SAMPLE_TAKEN, RESULT_UPLOADED, REVIEWED, CANCELLED)
}

/**
 * What a doctor may do with a request or result. The database enforces the
 * same rules; these just decide which buttons the app shows.
 */
object InvestigationRules {

    /** Still waiting for a result (shown as "pending" on the dashboard). */
    fun isAwaitingResult(r: InvestigationRequest): Boolean =
        r.status == InvestigationStatus.REQUESTED || r.status == InvestigationStatus.SAMPLE_TAKEN

    /** A result came in and the doctor hasn't marked it reviewed yet. */
    fun isAwaitingReview(r: InvestigationRequest): Boolean = r.status == InvestigationStatus.RESULT_UPLOADED

    fun canReview(r: InvestigationRequest): Boolean = isAwaitingReview(r)

    fun canCancel(r: InvestigationRequest): Boolean = isAwaitingResult(r)

    /** Doctors can add a result unless the request is closed. */
    fun canAddResult(r: InvestigationRequest): Boolean =
        r.status != InvestigationStatus.CANCELLED && r.status != InvestigationStatus.REVIEWED

    /** Results uploaded by the department can only be marked "entered in error", never edited. */
    fun canEditResult(result: InvestigationResult): Boolean = !result.isFromDepartment

    /** Requests sent to a department can't be edited once the department has started on them. */
    fun canEditRequest(r: InvestigationRequest): Boolean =
        r.status == InvestigationStatus.REQUESTED || (r.facilityId == null && isAwaitingResult(r))
}

/** Counts for the home screen. */
data class InvestigationCounts(val awaitingResult: Int, val awaitingReview: Int)

/** A request waiting for review, with its patient, for the home screen list. */
data class ResultToReview(val patient: Patient, val request: InvestigationRequest)

object InvestigationDashboard {
    fun counts(records: List<PatientRecord>): InvestigationCounts {
        val requests = records.filterNot { it.patient.isDeleted }.flatMap { it.investigationRequests }
        return InvestigationCounts(
            awaitingResult = requests.count(InvestigationRules::isAwaitingResult),
            awaitingReview = requests.count(InvestigationRules::isAwaitingReview),
        )
    }

    /** Results waiting for review, the oldest first (they have waited longest). */
    fun toReview(records: List<PatientRecord>): List<ResultToReview> =
        records.filterNot { it.patient.isDeleted }
            .flatMap { r -> r.investigationRequests.filter(InvestigationRules::isAwaitingReview).map { ResultToReview(r.patient, it) } }
            .sortedBy { Dates.sortKey(it.request.resultedAt ?: it.request.updatedAt) }
}

/**
 * Common tests, so typed values get the usual unit and adult reference range
 * filled in. Ranges vary between labs; they are a starting point the user
 * can change for each result, used only to highlight values.
 */
object LabCatalog {
    data class Test(val name: String, val unit: String?, val low: Double?, val high: Double?)

    val TESTS: List<Test> = listOf(
        Test("Hb", "g/dL", 12.0, 17.0),
        Test("WBC", "×10³/µL", 4.0, 11.0),
        Test("Platelets", "×10³/µL", 150.0, 400.0),
        Test("Haematocrit", "%", 36.0, 50.0),
        Test("Na", "mmol/L", 135.0, 145.0),
        Test("K", "mmol/L", 3.5, 5.1),
        Test("Urea", "mg/dL", 15.0, 45.0),
        Test("Creatinine", "mg/dL", 0.6, 1.3),
        Test("Glucose (random)", "mg/dL", 70.0, 140.0),
        Test("Glucose (fasting)", "mg/dL", 70.0, 100.0),
        Test("HbA1c", "%", 4.0, 5.7),
        Test("ALT", "U/L", 7.0, 56.0),
        Test("AST", "U/L", 10.0, 40.0),
        Test("Total bilirubin", "mg/dL", 0.1, 1.2),
        Test("Albumin", "g/dL", 3.5, 5.0),
        Test("ALP", "U/L", 44.0, 147.0),
        Test("Amylase", "U/L", 30.0, 110.0),
        Test("Lipase", "U/L", 10.0, 140.0),
        Test("PT", "s", 11.0, 13.5),
        Test("INR", null, 0.8, 1.2),
        Test("aPTT", "s", 25.0, 35.0),
        Test("CRP", "mg/L", 0.0, 5.0),
        Test("ESR", "mm/h", 0.0, 20.0),
        Test("Lactate", "mmol/L", 0.5, 2.2),
        Test("TSH", "mIU/L", 0.4, 4.0),
        Test("CEA", "ng/mL", 0.0, 5.0),
        Test("CA 19-9", "U/mL", 0.0, 37.0),
    )

    /** Groups of tests often requested together; picking one adds all of its tests. */
    val PANELS: Map<String, List<String>> = mapOf(
        "CBC" to listOf("Hb", "WBC", "Platelets", "Haematocrit"),
        "Kidney function" to listOf("Urea", "Creatinine", "Na", "K"),
        "Liver function" to listOf("ALT", "AST", "Total bilirubin", "Albumin", "ALP"),
        "Coagulation" to listOf("PT", "INR", "aPTT"),
    )

    /** Request suggestions per kind, shown as chips. Free text is always allowed. */
    val REQUEST_SUGGESTIONS: Map<String, List<String>> = mapOf(
        "lab" to PANELS.keys.toList() + listOf("Glucose (random)", "HbA1c", "CRP", "Amylase", "Blood group", "Urine analysis"),
        "imaging" to listOf("Chest X-ray", "Abdominal ultrasound", "CT abdomen & pelvis", "CT chest", "MRI", "Doppler ultrasound", "ECG"),
        "pathology" to listOf("Histopathology", "Cytology", "Frozen section"),
        "other" to emptyList(),
    )

    fun find(name: String): Test? = testKey(name).let { key -> TESTS.firstOrNull { testKey(it.name) == key } }

    /** The typed-value rows to start a result with, from the requested tests (panels expanded). */
    fun startingValues(requestedTests: List<String>): List<LabValue> =
        requestedTests.flatMap { PANELS[it] ?: listOf(it) }
            .distinct()
            .map { name -> find(name)?.let { LabValue(it.name, unit = it.unit, low = it.low, high = it.high) } ?: LabValue(name) }
}

data class LabPoint(
    val at: Instant,
    val value: Double,
    val unit: String?,
    val abnormal: Boolean,
    val low: Double? = null,
    val high: Double? = null,
)

/** Lab values over time, for the trend chart next to the vital signs. */
object LabTrends {
    /** Test names with at least one number, most often measured first. */
    fun available(results: List<InvestigationResult>): List<String> =
        results.flatMap { r -> r.labValues.filter { it.value != null }.map { it.test.trim() } }
            .groupBy { testKey(it) }
            .values
            .sortedByDescending { it.size }
            .map { names -> names.first() }

    /** Points for one test, oldest first. Names match ignoring case and spacing ("hb" = "Hb"). */
    fun series(results: List<InvestigationResult>, test: String): List<LabPoint> {
        val key = testKey(test)
        return results.flatMap { r ->
            val at = Dates.instant(r.resultDate ?: r.createdAt) ?: return@flatMap emptyList()
            r.labValues.filter { testKey(it.test) == key && it.value != null }
                .map { LabPoint(at, it.value!!, it.unit, it.isAbnormal, it.low, it.high) }
        }.sortedBy { it.at }
    }
}

/** A lab/radiology department that requests can be sent to. */
@Serializable
data class Facility(
    val id: String,
    val name: String,
    val kind: String,
    val hospital: String? = null,
) {
    val label: String get() = listOfNotNull(name, hospital?.takeIf { it.isNotBlank() }).joinToString(" – ")
}

object FacilityValidator {
    val KINDS = listOf("lab", "radiology", "pathology", "other")

    fun isValidName(name: String): Boolean = name.trim().length in 2..120

    /** True if an equivalent department already exists (same kind, name and hospital, ignoring case and spaces). */
    fun isDuplicate(existing: List<Facility>, name: String, kind: String, hospital: String?): Boolean =
        existing.any {
            it.kind == kind && it.name.trim().equals(name.trim(), ignoreCase = true) &&
                it.hospital.orEmpty().trim().equals(hospital.orEmpty().trim(), ignoreCase = true)
        }

    /** Department kinds that can receive each kind of request. */
    fun accepts(facilityKind: String, investigationKind: String): Boolean = when (investigationKind) {
        "lab" -> facilityKind == "lab"
        "imaging" -> facilityKind == "radiology"
        "pathology" -> facilityKind == "pathology"
        else -> true
    }
}

// ---- What lab/radiology staff see (from the staff_* database functions) ----

@Serializable
data class WorklistItem(
    val id: String,
    val patientId: String,
    val kind: String,
    val tests: List<String> = emptyList(),
    val urgency: String = "routine",
    val status: String,
    val requestedAt: String? = null,
    val patientName: String,
    val patientSex: String? = null,
    val fileNumber: String? = null,
    val ageYears: Int? = null,
    val requestedBy: String? = null,
)

@Serializable
data class StaffPatient(
    val fullName: String,
    val sex: String? = null,
    val fileNumber: String? = null,
    val ageYears: Int? = null,
)

@Serializable
data class StaffAllergy(val allergen: String, val severity: String = "moderate", val reaction: String? = null)

@Serializable
data class StaffResult(
    val title: String,
    val resultDate: String? = null,
    val reportText: String? = null,
    val labValues: List<LabValue> = emptyList(),
)

@Serializable
data class StaffRequestDetail(
    val id: String,
    val patientId: String,
    val kind: String,
    val tests: List<String> = emptyList(),
    val urgency: String = "routine",
    val status: String,
    val clinicalNotes: String? = null,
    val requestedAt: String? = null,
    val requestedBy: String? = null,
    val patient: StaffPatient,
    val allergies: List<StaffAllergy> = emptyList(),
    val previousResults: List<StaffResult> = emptyList(),
    val thisRequestResults: List<StaffResult> = emptyList(),
)
