package com.eliormachlev.currencix.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A trip: saved carts grouped under a [name], their spending added up in the
 * [currency] of home, against an optional [budget] in it. Optionally where
 * it's to ([destination], the currency paid in there) and when ([start] to
 * [end], both days included), which the app uses to help before and during
 * it: a per-day budget, the receipt language kept until it ends, a checklist.
 */
data class Trip(
    val id: String,
    val name: String,
    val currency: Currency,
    val budget: BigDecimal? = null,
    val destination: Currency? = null,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
) {
    /** How many days it lasts, both ends included; null without both dates. */
    val days: Long? get() = if (start != null && end != null) ChronoUnit.DAYS.between(start, end) + 1 else null

    /** When it's over: the last minute of its last day in [zone]; null without an end. */
    fun endsAt(zone: ZoneId = ZoneId.systemDefault()): Instant? = end?.atTime(LocalTime.MAX)?.atZone(zone)?.toInstant()

    /** Whether it's over on [today]. */
    fun hasEnded(today: LocalDate): Boolean = end?.isBefore(today) == true

    /** The days of it gone by on [today] (that one included), within the trip; null without a start or before it. */
    fun daysSoFar(today: LocalDate): Long? {
        val from = start?.takeIf { !today.isBefore(it) } ?: return null
        val until = end?.takeIf { it.isBefore(today) } ?: today
        return ChronoUnit.DAYS.between(from, until) + 1
    }
}
