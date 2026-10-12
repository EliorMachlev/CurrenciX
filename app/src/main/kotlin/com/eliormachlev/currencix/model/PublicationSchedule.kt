package com.eliormachlev.currencix.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

// How long after a provider's stated publication time the refresh runs, so
// a table published a little late (or a mirror like Frankfurter.app, which
// copies the ECB's) is already there.
private val REFRESH_MARGIN = 30.minutes.toJavaDuration()

/**
 * When a provider publishes new rates. Auto-refresh follows a [Published]
 * schedule exactly (once a day or a month, just after publication), and
 * only a [Frequent] provider's refresh runs at the user's
 * [RefreshInterval] — refreshing a daily table every hour gets the same
 * table again.
 */
sealed interface PublicationSchedule {
    val cadence: UpdateCadence

    /** Many times a day, every [cadence] (hourly or more often): refreshed every [RefreshInterval]. */
    data class Frequent(
        override val cadence: UpdateCadence,
    ) : PublicationSchedule

    /** At a known time, in the publisher's own [ZoneId]. */
    sealed interface Published : PublicationSchedule {
        /** The first publication time on or after [date], in its own zone. */
        fun publicationOnOrAfter(date: LocalDate): ZonedDateTime

        /**
         * When to refresh next after [now]: [REFRESH_MARGIN] past the next
         * publication whose refresh time hasn't come yet.
         */
        fun nextRefreshAfter(now: Instant): Instant {
            val first = publicationOnOrAfter(now.atZone(zone).toLocalDate())
            val publication =
                if (first.plus(REFRESH_MARGIN).toInstant().isAfter(now)) {
                    first
                } else {
                    publicationOnOrAfter(first.toLocalDate().plusDays(1))
                }
            return publication.plus(REFRESH_MARGIN).toInstant()
        }

        /** The publisher's time zone, which its stated time is in. */
        val zone: ZoneId
    }

    /**
     * Every day at [at] in [zone]. A day with no new table (a weekend, a
     * holiday) costs one request that brings back the last one.
     */
    data class Daily(
        val at: LocalTime,
        override val zone: ZoneId,
    ) : Published {
        override val cadence = UpdateCadence.DAILY

        override fun publicationOnOrAfter(date: LocalDate): ZonedDateTime = date.atTime(at).atZone(zone)
    }

    /** Once a month, on [dayOfMonth] at [at] in [zone]. */
    data class Monthly(
        val dayOfMonth: Int,
        val at: LocalTime,
        override val zone: ZoneId,
    ) : Published {
        override val cadence = UpdateCadence.MONTHLY

        override fun publicationOnOrAfter(date: LocalDate): ZonedDateTime {
            val thisMonth = date.withDayOfMonth(dayOfMonth)
            val day = if (thisMonth.isBefore(date)) thisMonth.plusMonths(1) else thisMonth
            return day.atTime(at).atZone(zone)
        }
    }
}
