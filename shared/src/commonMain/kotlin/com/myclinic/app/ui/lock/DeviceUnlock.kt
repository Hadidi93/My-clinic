package com.myclinic.app.ui.lock

import androidx.compose.runtime.Composable

/**
 * The phone's own unlock: fingerprint / face / PIN on Android, Face ID /
 * Touch ID / passcode on iPhone. [available] is false when the phone has no
 * screen lock at all; [openSettings] then takes the user to set one.
 */
class DeviceUnlock(val available: Boolean, val prompt: () -> Unit, val openSettings: () -> Unit)

@Composable
expect fun rememberDeviceUnlock(
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
): DeviceUnlock
