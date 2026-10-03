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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.myclinic.app.R
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.SecondaryButton

/** What the phone can use to unlock: fingerprint/face, or the phone's own PIN/pattern/password. */
private fun authenticators(): Int =
    // Android 10 only allows "weak" biometrics together with the phone PIN.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/**
 * Covers the app until the user proves it's them. It is drawn on top of the
 * screens (which stay open underneath, so nothing typed is lost) and takes
 * every touch. If the phone has no screen lock at all, the app can't be used
 * until one is set, because the records would otherwise be one tap away.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var canAuthenticate by remember { mutableStateOf(BiometricManager.from(context).canAuthenticate(authenticators())) }
    // Coming back from Settings after setting a screen lock: check again.
    LifecycleResumeEffect(Unit) {
        canAuthenticate = BiometricManager.from(context).canAuthenticate(authenticators())
        onPauseOrDispose { }
    }
    var error by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)

    fun prompt() {
        val host = activity ?: return
        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) error = errString.toString()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators())
                .build(),
        )
    }

    val ready = canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS
    LaunchedEffect(ready) { if (ready) prompt() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            // Swallow every touch so nothing underneath can be used.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ready) {
                Text(stringResource(R.string.lock_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                PrimaryButton(stringResource(R.string.lock_unlock), onClick = { error = null; prompt() })
            } else {
                Text(stringResource(R.string.lock_no_screen_lock), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                PrimaryButton(stringResource(R.string.lock_open_settings), onClick = {
                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
            SecondaryButton(stringResource(R.string.sign_out), onClick = onSignOut)
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

