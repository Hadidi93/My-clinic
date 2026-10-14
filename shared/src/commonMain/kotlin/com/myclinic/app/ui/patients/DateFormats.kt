package com.myclinic.app.ui.patients

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** "12 Oct 2026" in the phone's (app's) language. */
expect fun formatLocalDate(date: LocalDate): String

/** "12 Oct 2026, 14:30" in the phone's time zone and the app's language. */
expect fun formatLocalDateTime(instant: Instant): String
