package com.myclinic.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.myclinic.app.ui.admin.AdminApprovalsScreen
import com.myclinic.app.ui.auth.CheckEmailScreen
import com.myclinic.app.ui.auth.ForgotPasswordScreen
import com.myclinic.app.ui.auth.LoginScreen
import com.myclinic.app.ui.auth.SignUpScreen
import com.myclinic.app.ui.home.HomeScreen
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

/** Screens for signed-out users. */
@Composable
fun AuthNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginScreen(
                onCreateAccount = { nav.navigate(SignUpRoute) },
                onForgotPassword = { nav.navigate(ForgotPasswordRoute) },
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
            )
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
