package com.myclinic.app.ui.components

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.myclinic.app.R

/** The app's current language code: "ar" or "en". */
fun currentAppLanguage(): String =
    if (AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("ar")) "ar" else "en"

/** Switches the whole app to [language] ("en" or "ar"); Arabic also flips the layout right-to-left. */
fun setAppLanguage(language: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
}

/** Two chips: English | العربية. */
@Composable
fun LanguageSwitch(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == "en",
            onClick = { onSelect("en") },
            label = { Text(stringResource(R.string.language_english)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        FilterChip(
            selected = selected == "ar",
            onClick = { onSelect("ar") },
            label = { Text(stringResource(R.string.language_arabic)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}
