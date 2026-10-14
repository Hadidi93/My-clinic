package com.myclinic.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Asks once for permission to show notifications. If the user says no, the
 * app still works; the bell list inside the app shows everything.
 */
@Composable
expect fun RequestNotificationPermission()
