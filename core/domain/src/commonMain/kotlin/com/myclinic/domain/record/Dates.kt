package com.myclinic.domain.record

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearsUntil

/** Parsing of the ISO date/time text the server sends. Anything unparseable becomes null. */
object Dates {

    /** Today on this device's calendar. */
    fun today(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate = Clock.System.todayIn(zone)

    fun now(): Instant = Clock.System.now()

    /** "2026-09-30" or a full timestamp -> the local calendar date. */
    fun localDate(value: String?, zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            if (value.length == 10) LocalDate.parse(value) else instant(value)?.toLocalDateTime(zone)?.date
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun instant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            if (value.length == 10) {
                LocalDate.parse(value).atStartOfDayIn(TimeZone.UTC)
            } else {
                Instant.parse(value.replace(' ', 'T'))
            }
        } catch (e: IllegalArgumentException) {
            try {
                LocalDateTime.parse(value).toInstant(TimeZone.UTC)
            } catch (e2: IllegalArgumentException) {
                null
            }
        }
    }

    /** A sortable number; missing dates sort as oldest. */
    fun sortKey(value: String?): Long = instant(value)?.toEpochMilliseconds() ?: Long.MIN_VALUE

    /** Timestamp text for "now" in the format the server accepts. */
    fun nowIso(now: Instant = now()): String = now.toString()

    /** Age in whole years from date of birth, falling back to the recorded age. */
    fun age(dateOfBirth: String?, ageYears: Int?, today: LocalDate): Int? =
        localDate(dateOfBirth)?.let { it.yearsUntil(today).takeIf { y -> y >= 0 } } ?: ageYears
}
