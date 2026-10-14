package com.myclinic.app.ui.components

import androidx.compose.runtime.Composable

/** The phone's own "back" (Android back button/gesture). iPhone has no system back: screens use their back arrow. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
