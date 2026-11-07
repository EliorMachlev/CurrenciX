package com.eliormachlev.currencix.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Converts a Unix timestamp to a LocalDate
 */
fun Long.toLocalDate(): LocalDate =
    Instant
        .ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()

/**
 * Converts a LocalDate to a Unix timestamp
 */
fun LocalDate.toMillis() =
    this
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

/**
 * Returns only the date portion of a combined "date time" pattern (drops anything from the first
 * space onward). Safe to call on a date-only pattern.
 */
fun stripTimePattern(pattern: String): String = pattern.substringBefore(' ').trim()

/** This ISO date ("2026-03-04"), or null when it isn't one. */
fun String.toLocalDateOrNull(): LocalDate? =
    try {
        LocalDate.parse(this)
    } catch (_: DateTimeParseException) {
        null
    }

/** Today's date, again each time midnight passes (in the phone's time zone). */
fun todayEachDay(): Flow<LocalDate> =
    flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now.toLocalDate())
            delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1)
        }
    }
