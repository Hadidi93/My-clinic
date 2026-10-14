package com.myclinic.app.ui.components

import com.myclinic.shared.resources.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp

/** The app's current language code: "ar" or "en". */
expect fun currentAppLanguage(): String

/** Switches the whole app to [language] ("en" or "ar"); Arabic also flips the layout right-to-left. */
expect fun setAppLanguage(language: String)

/** Two chips: English | العربية. */
@Composable
fun LanguageSwitch(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == "en",
            onClick = { onSelect("en") },
            label = { Text(stringResource(Res.string.language_english)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        FilterChip(
            selected = selected == "ar",
            onClick = { onSelect("ar") },
            label = { Text(stringResource(Res.string.language_arabic)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}
