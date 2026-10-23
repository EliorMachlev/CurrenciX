package com.eliormachlev.currencix.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * How often the background auto-refresh runs, as the user picks it in
 * Settings → Exchange rates. Stored as [minutes]: never change an entry's
 * duration, add a new one instead. 15 minutes is WorkManager's shortest
 * periodic interval, so nothing goes below it.
 */
enum class RefreshInterval(
    val duration: Duration,
) {
    MINUTES_15(15.minutes),
    MINUTES_30(30.minutes),
    HOUR_1(1.hours),
    HOURS_3(3.hours),
    HOURS_6(6.hours),
    HOURS_12(12.hours),
    HOURS_24(24.hours),
    ;

    val minutes: Long get() = duration.inWholeMinutes

    companion object {
        val DEFAULT: RefreshInterval = HOUR_1

        /** The interval stored as [minutes]; [DEFAULT] for nothing stored, or a value no entry has. */
        fun fromMinutes(minutes: Int?): RefreshInterval = entries.firstOrNull { it.minutes == minutes?.toLong() } ?: DEFAULT
    }
}
