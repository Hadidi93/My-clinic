package com.myclinic.app.ui.navigation

import kotlinx.serialization.Serializable

// Screen addresses ("routes"). Type-safe: the compiler checks every navigate() call.
@Serializable object LoginRoute
@Serializable object SignUpRoute
@Serializable object ForgotPasswordRoute
@Serializable data class CheckEmailRoute(val email: String)

@Serializable object HomeRoute
@Serializable object ProfileRoute
@Serializable object AdminApprovalsRoute
@Serializable data class PatientListRoute(val dateFilter: String = "ALL", val quickAdd: Boolean = false)
@Serializable data class PatientDetailRoute(val patientId: String)
/**
 * Add (entryId = null) or edit one entry of [table] ("patients" = personal data).
 * [requestId]: a new result for that investigation request.
 */
@Serializable data class EntryFormRoute(
    val patientId: String,
    val table: String,
    val entryId: String? = null,
    val requestId: String? = null,
)
@Serializable data class InvestigationRoute(val patientId: String, val requestId: String)
/** [consultFile]: the file is in a consult conversation (consult-files), not the patient record. */
@Serializable data class FileViewerRoute(
    val path: String,
    val mimeType: String,
    val title: String? = null,
    val consultFile: Boolean = false,
)

// Consults, referrals, notifications
@Serializable object ConsultsRoute
@Serializable data class ConsultRoute(val consultId: String)
@Serializable data class NewConsultRoute(val patientId: String)
@Serializable object ReferralsRoute
@Serializable data class NewReferralRoute(val patientId: String)
@Serializable object NotificationsRoute
/** A patient's access log, or (patientId = null) the admin's app-wide activity log. */
@Serializable data class AccessLogRoute(val patientId: String? = null)
/** De-identified spreadsheet of the doctor's own patients, for research or audit. */
@Serializable object ResearchExportRoute

// Lab/radiology staff
@Serializable object StaffHomeRoute
@Serializable data class StaffRequestRoute(val requestId: String)
