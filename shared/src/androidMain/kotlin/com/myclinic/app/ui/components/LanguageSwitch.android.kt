package com.myclinic.app.ui.components

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

actual fun currentAppLanguage(): String =
    if (AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("ar")) "ar" else "en"

// AppCompat restarts the screens in the new language (and right-to-left for Arabic).
actual fun setAppLanguage(language: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
}
