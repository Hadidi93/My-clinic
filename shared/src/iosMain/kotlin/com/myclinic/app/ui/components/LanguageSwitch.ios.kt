package com.myclinic.app.ui.components

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

/** The language iOS gave the app (Settings → My Clinic → Language, or the phone's language). */
actual fun currentAppLanguage(): String =
    if ((NSLocale.preferredLanguages.firstOrNull() as? String)?.startsWith("ar") == true) "ar" else "en"

/** iOS applies a new app language the next time the app starts. */
actual fun setAppLanguage(language: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(language), forKey = "AppleLanguages")
}
