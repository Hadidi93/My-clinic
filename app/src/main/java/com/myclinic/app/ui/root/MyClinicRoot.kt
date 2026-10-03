package com.myclinic.app.ui.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myclinic.app.R
import com.myclinic.app.ui.auth.ResetPasswordScreen
import com.myclinic.app.ui.components.FullScreenError
import com.myclinic.app.ui.components.FullScreenLoading
import com.myclinic.app.ui.navigation.AuthNavHost
import com.myclinic.app.ui.navigation.MainNavHost
import com.myclinic.app.ui.navigation.StaffNavHost
import com.myclinic.app.ui.profile.ProfileScreen

/** Top of the screen tree: picks the flow that matches the current [RootState]. */
@Composable
fun MyClinicRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val linkMessage by viewModel.linkMessage.collectAsStateWithLifecycle()
    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()
    when (val s = state) {
        RootState.Loading -> FullScreenLoading()
        RootState.SignedOut -> AuthNavHost(linkMessage = linkMessage, onDismissLinkMessage = viewModel::clearLinkMessage)
        RootState.PasswordRecovery -> ResetPasswordScreen()
        RootState.ProfileLoadFailed -> FullScreenError(
            message = stringResource(R.string.error_profile_load),
            onRetry = viewModel::loadProfile,
            onSignOut = viewModel::signOut,
        )
        is RootState.NeedsProfile -> ProfileScreen(setupMode = true, onBack = null, onSignOut = viewModel::signOut)
        is RootState.Ready -> if (s.doctor.isStaff) {
            StaffNavHost(staff = s.doctor, onSignOut = viewModel::signOut, openRequest, viewModel::onOpenHandled)
        } else {
            MainNavHost(doctor = s.doctor, onSignOut = viewModel::signOut, openRequest, viewModel::onOpenHandled)
        }
    }
}
