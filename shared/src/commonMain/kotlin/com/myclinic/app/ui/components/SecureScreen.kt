package com.myclinic.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Wrap any screen that shows patient or licence data in SecureScreen { ... }.
 * Android blocks screenshots and screen recording and blanks the recent-apps
 * preview (FLAG_SECURE). iPhone can't block screenshots, so it hides the
 * screen while it is being recorded or mirrored, and the app covers itself
 * in the app switcher.
 */
@Composable
expect fun SecureScreen(content: @Composable () -> Unit)
