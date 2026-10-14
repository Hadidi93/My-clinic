package com.myclinic.app.ui.patients

import com.myclinic.app.ui.components.currentAppLanguage
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName

private fun formatter(dateStyle: ULong, timeStyle: ULong) = NSDateFormatter().apply {
    this.dateStyle = dateStyle
    this.timeStyle = timeStyle
    locale = NSLocale(localeIdentifier = if (currentAppLanguage() == "ar") "ar" else "en_GB")
}

private fun Instant.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1000.0)

actual fun formatLocalDate(date: LocalDate): String =
    // A calendar date: format it at UTC midnight in UTC, so no time zone moves it a day.
    formatter(NSDateFormatterMediumStyle, NSDateFormatterNoStyle)
        .apply { timeZone = requireNotNull(NSTimeZone.timeZoneWithName("UTC")) }
        .stringFromDate(date.atStartOfDayIn(TimeZone.UTC).toNSDate())

actual fun formatLocalDateTime(instant: Instant): String =
    formatter(NSDateFormatterMediumStyle, NSDateFormatterShortStyle).stringFromDate(instant.toNSDate())
