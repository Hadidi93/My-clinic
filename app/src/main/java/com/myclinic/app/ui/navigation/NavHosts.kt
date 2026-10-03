package com.myclinic.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.myclinic.app.data.auth.LinkMessage
import com.myclinic.app.ui.admin.AdminApprovalsScreen
import com.myclinic.app.ui.auth.CheckEmailScreen
import com.myclinic.app.ui.auth.ForgotPasswordScreen
import com.myclinic.app.ui.auth.LoginScreen
import com.myclinic.app.ui.auth.SignUpScreen
import com.myclinic.app.ui.home.HomeScreen
import com.myclinic.app.ui.consults.ConsultDetailScreen
import com.myclinic.app.ui.consults.ConsultsScreen
import com.myclinic.app.ui.consults.NewConsultScreen
import com.myclinic.app.ui.consults.NewReferralScreen
import com.myclinic.app.ui.consults.NotificationsScreen
import com.myclinic.app.ui.consults.ReferralsScreen
import com.myclinic.app.ui.files.FileViewerScreen
import com.myclinic.app.ui.audit.AccessLogScreen
import com.myclinic.domain.consult.NotificationKind
import com.myclinic.app.ui.patients.EntryFormScreen
import com.myclinic.app.ui.patients.InvestigationScreen
import com.myclinic.app.ui.staff.StaffHomeScreen
import com.myclinic.app.ui.staff.StaffRequestScreen
import com.myclinic.domain.record.Attachment
import com.myclinic.app.ui.patients.PatientDetailScreen
import com.myclinic.app.ui.patients.PatientListScreen
import com.myclinic.domain.record.DateFilter
import com.myclinic.app.ui.profile.ProfileScreen
import com.myclinic.domain.model.Doctor
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

// Lab/radiology staff
@Serializable object StaffHomeRoute
@Serializable data class StaffRequestRoute(val requestId: String)

/** Screens for signed-out users. [linkMessage] explains an email link that couldn't sign in. */
@Composable
fun AuthNavHost(linkMessage: LinkMessage?, onDismissLinkMessage: () -> Unit) {
    val nav = rememberNavController()
    // A link message belongs on the sign-in screen, wherever the user was.
    LaunchedEffect(linkMessage) {
        if (linkMessage != null) nav.popBackStack(LoginRoute, inclusive = false)
    }
    NavHost(navController = nav, startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginScreen(
                onCreateAccount = { nav.navigate(SignUpRoute) },
                onForgotPassword = { nav.navigate(ForgotPasswordRoute) },
                linkMessage = linkMessage,
                onDismissLinkMessage = onDismissLinkMessage,
            )
        }
        composable<SignUpRoute> {
            SignUpScreen(
                onBack = { nav.popBackStack() },
                onRegistered = { email ->
                    nav.navigate(CheckEmailRoute(email)) { popUpTo(LoginRoute) }
                },
            )
        }
        composable<CheckEmailRoute> { entry ->
            CheckEmailScreen(
                email = entry.toRoute<CheckEmailRoute>().email,
                onBackToSignIn = { nav.popBackStack(LoginRoute, inclusive = false) },
            )
        }
        composable<ForgotPasswordRoute> {
            ForgotPasswordScreen(onBack = { nav.popBackStack() })
        }
    }
}

/**
 * Screens for signed-in doctors with a complete profile. [openRequest] is the
 * kind of a notification the user tapped to open the app.
 */
@Composable
fun MainNavHost(doctor: Doctor, onSignOut: () -> Unit, openRequest: String?, onOpenHandled: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(openRequest) {
        when {
            openRequest == null -> return@LaunchedEffect
            NotificationKind.isConsult(openRequest) -> nav.navigate(ConsultsRoute) { launchSingleTop = true }
            NotificationKind.isReferral(openRequest) -> nav.navigate(ReferralsRoute) { launchSingleTop = true }
            else -> nav.navigate(NotificationsRoute) { launchSingleTop = true }
        }
        onOpenHandled()
    }
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                doctor = doctor,
                onOpenProfile = { nav.navigate(ProfileRoute) },
                onOpenAdmin = { nav.navigate(AdminApprovalsRoute) },
                onOpenPatients = { filter -> nav.navigate(PatientListRoute(dateFilter = filter.name)) },
                onQuickAdd = { nav.navigate(PatientListRoute(quickAdd = true)) },
                onOpenPatient = { id -> nav.navigate(PatientDetailRoute(id)) },
                onOpenInvestigation = { patientId, requestId -> nav.navigate(InvestigationRoute(patientId, requestId)) },
                onOpenConsults = { nav.navigate(ConsultsRoute) },
                onOpenReferrals = { nav.navigate(ReferralsRoute) },
                onOpenNotifications = { nav.navigate(NotificationsRoute) },
                onOpenActivityLog = { nav.navigate(AccessLogRoute(null)) },
            )
        }
        composable<ConsultsRoute> {
            ConsultsScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(ConsultRoute(it)) })
        }
        composable<ConsultRoute> {
            ConsultDetailScreen(
                onBack = { nav.popBackStack() },
                onOpenConsultFile = { path, mime -> nav.navigate(FileViewerRoute(path, mime, consultFile = true)) },
                onOpenRecordFile = { nav.navigate(it.viewerRoute()) },
            )
        }
        composable<NewConsultRoute> {
            NewConsultScreen(
                onClose = { nav.popBackStack() },
                onCreated = { id -> nav.navigate(ConsultRoute(id)) { popUpTo<NewConsultRoute> { inclusive = true } } },
            )
        }
        composable<ReferralsRoute> {
            ReferralsScreen(onBack = { nav.popBackStack() }, onOpenPatient = { nav.navigate(PatientDetailRoute(it)) })
        }
        composable<NewReferralRoute> {
            NewReferralScreen(
                onClose = { nav.popBackStack() },
                onSent = { nav.navigate(ReferralsRoute) { popUpTo<NewReferralRoute> { inclusive = true } } },
            )
        }
        composable<NotificationsRoute> {
            NotificationsScreen(
                onBack = { nav.popBackStack() },
                onOpen = { n ->
                    val consultId = n.refId
                    when {
                        NotificationKind.isConsult(n.kind) && consultId != null -> nav.navigate(ConsultRoute(consultId))
                        NotificationKind.isReferral(n.kind) -> nav.navigate(ReferralsRoute)
                        else -> Unit
                    }
                },
            )
        }
        composable<PatientListRoute> { entry ->
            val route = entry.toRoute<PatientListRoute>()
            PatientListScreen(
                initialDateFilter = runCatching { DateFilter.valueOf(route.dateFilter) }.getOrDefault(DateFilter.ALL),
                startWithQuickAdd = route.quickAdd,
                onBack = { nav.popBackStack() },
                onOpenPatient = { id -> nav.navigate(PatientDetailRoute(id)) },
            )
        }
        composable<PatientDetailRoute> {
            PatientDetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { patientId, table, entryId -> nav.navigate(EntryFormRoute(patientId, table.tableName, entryId)) },
                onOpenInvestigation = { patientId, requestId -> nav.navigate(InvestigationRoute(patientId, requestId)) },
                onConsult = { nav.navigate(NewConsultRoute(it)) },
                onRefer = { nav.navigate(NewReferralRoute(it)) },
                onOpenReferrals = { nav.navigate(ReferralsRoute) },
                onOpenAccessLog = { nav.navigate(AccessLogRoute(it)) },
            )
        }
        composable<AccessLogRoute> {
            AccessLogScreen(onBack = { nav.popBackStack() })
        }
        composable<EntryFormRoute> {
            EntryFormScreen(onClose = { nav.popBackStack() }, onOpenFile = { nav.navigate(it.viewerRoute()) })
        }
        composable<InvestigationRoute> { entry ->
            val route = entry.toRoute<InvestigationRoute>()
            InvestigationScreen(
                onBack = { nav.popBackStack() },
                onEdit = { table, entryId, requestId ->
                    nav.navigate(EntryFormRoute(route.patientId, table.tableName, entryId, requestId))
                },
                onOpenFile = { nav.navigate(it.viewerRoute()) },
            )
        }
        composable<FileViewerRoute> {
            FileViewerScreen(onBack = { nav.popBackStack() })
        }
        composable<ProfileRoute> {
            ProfileScreen(setupMode = false, onBack = { nav.popBackStack() }, onSignOut = onSignOut)
        }
        composable<AdminApprovalsRoute> {
            // Also enforced on the server: non-admins get nothing back.
            if (doctor.isAdmin) {
                AdminApprovalsScreen(onBack = { nav.popBackStack() })
            } else {
                LaunchedEffect(Unit) { nav.popBackStack() }
            }
        }
    }
}

/** Screens for lab/radiology staff: the department inbox, one request, and the profile. */
@Composable
fun StaffNavHost(staff: Doctor, onSignOut: () -> Unit, openRequest: String?, onOpenHandled: () -> Unit) {
    val nav = rememberNavController()
    // A tapped "new request" notification: the inbox is the first screen anyway.
    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            nav.popBackStack(StaffHomeRoute, inclusive = false)
            onOpenHandled()
        }
    }
    NavHost(navController = nav, startDestination = StaffHomeRoute) {
        composable<StaffHomeRoute> {
            StaffHomeScreen(
                staff = staff,
                onOpenProfile = { nav.navigate(ProfileRoute) },
                onOpenRequest = { nav.navigate(StaffRequestRoute(it)) },
            )
        }
        composable<StaffRequestRoute> {
            StaffRequestScreen(onBack = { nav.popBackStack() })
        }
        composable<ProfileRoute> {
            ProfileScreen(setupMode = false, onBack = { nav.popBackStack() }, onSignOut = onSignOut)
        }
    }
}

private fun Attachment.viewerRoute() = FileViewerRoute(storagePath, mimeType, caption ?: fileName)
