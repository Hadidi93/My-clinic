package com.myclinic.app.ui.lock

import com.myclinic.shared.resources.*
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.SecondaryButton

/**
 * Covers the app until the user proves it's them. It is drawn on top of the
 * screens (which stay open underneath, so nothing typed is lost) and takes
 * every touch. If the phone has no screen lock at all, the app can't be used
 * until one is set, because the records would otherwise be one tap away.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit, onSignOut: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val unlock = rememberDeviceUnlock(
        title = stringResource(Res.string.lock_prompt_title),
        subtitle = stringResource(Res.string.lock_prompt_subtitle),
        onSuccess = onUnlocked,
        onError = { error = it },
    )
    fun prompt() = unlock.prompt()

    val ready = unlock.available
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
        Text(stringResource(Res.string.lock_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ready) {
                Text(stringResource(Res.string.lock_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                PrimaryButton(stringResource(Res.string.lock_unlock), onClick = { error = null; prompt() })
            } else {
                Text(stringResource(Res.string.lock_no_screen_lock), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                PrimaryButton(stringResource(Res.string.lock_open_settings), onClick = unlock.openSettings)
            }
            SecondaryButton(stringResource(Res.string.sign_out), onClick = onSignOut)
        }
    }
}
