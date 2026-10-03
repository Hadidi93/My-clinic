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
import com.myclinic.app.ui.files.FileViewerScreen
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
@Serializable data class FileViewerRoute(val path: String, val mimeType: String, val title: String? = null)

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

/** Screens for signed-in doctors with a complete profile. */
@Composable
fun MainNavHost(doctor: Doctor, onSignOut: () -> Unit) {
    val nav = rememberNavController()
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
            )
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
fun StaffNavHost(staff: Doctor, onSignOut: () -> Unit) {
    val nav = rememberNavController()
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
