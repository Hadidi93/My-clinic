package com.myclinic.app.ui.lock

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect

/** What the phone can use to unlock: fingerprint/face, or the phone's own PIN/pattern/password. */
private fun authenticators(): Int =
    // Android 10 only allows "weak" biometrics together with the phone PIN.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

@Composable
actual fun rememberDeviceUnlock(
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
): DeviceUnlock {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var canAuthenticate by remember { mutableIntStateOf(BiometricManager.from(context).canAuthenticate(authenticators())) }
    // Coming back from Settings after setting a screen lock: check again.
    LifecycleResumeEffect(Unit) {
        canAuthenticate = BiometricManager.from(context).canAuthenticate(authenticators())
        onPauseOrDispose { }
    }
    val prompt: () -> Unit = prompt@{
        val host = activity ?: return@prompt
        BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) onError(errString.toString())
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators())
                .build(),
        )
    }
    return DeviceUnlock(
        available = canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS,
        prompt = prompt,
        openSettings = {
            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
    )
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
