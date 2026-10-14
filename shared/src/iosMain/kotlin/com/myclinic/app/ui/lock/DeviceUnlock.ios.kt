package com.myclinic.app.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Face ID / Touch ID, falling back to the phone's passcode. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberDeviceUnlock(
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
): DeviceUnlock {
    val available = remember { LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null) }
    return DeviceUnlock(
        available = available,
        prompt = {
            LAContext().evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = subtitle) { ok, error ->
                dispatch_async(dispatch_get_main_queue()) {
                    val code = error?.code
                    when {
                        ok -> onSuccess()
                        code == LAErrorUserCancel || code == LAErrorSystemCancel || code == LAErrorAppCancel -> Unit
                        else -> onError(error?.localizedDescription ?: title)
                    }
                }
            }
        },
        openSettings = {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any?>(), completionHandler = null)
            }
        },
    )
}
