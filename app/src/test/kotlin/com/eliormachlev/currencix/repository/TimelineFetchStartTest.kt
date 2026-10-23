package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TimelineFetchStartTest {
    private val today = LocalDate.of(2026, 9, 30)
    private val yearAgo = today.minusYears(1)

    @Test
    fun `nothing cached fetches the whole span, a year at a time from today back`() {
        assertEquals(listOf(yearAgo..today), timelineFetchRanges(yearAgo, null, null, today))
        val threeYearsAgo = today.minusYears(3).minusMonths(2)
        assertEquals(
            listOf(
                yearAgo..today,
                today.minusYears(2)..yearAgo,
                today.minusYears(3)..today.minusYears(2),
                threeYearsAgo..today.minusYears(3),
            ),
            timelineFetchRanges(threeYearsAgo, null, null, today),
        )
    }

    @Test
    fun `a cache reaching back far enough fetches only the tail`() {
        val last = today.minusDays(2)
        assertEquals(listOf(last..today), timelineFetchRanges(yearAgo, yearAgo, last, today))
        // Weekends: the first published day can be a few days after the start.
        assertEquals(listOf(last..today), timelineFetchRanges(yearAgo, yearAgo.plusDays(3), last, today))
    }

    @Test
    fun `a longer span than the cache fetches only what it lacks, before what's cached`() {
        val fiveYearsAgo = today.minusYears(5)
        val ranges = timelineFetchRanges(fiveYearsAgo, yearAgo, today, today)
        assertEquals(today..today, ranges.first())
        assertEquals(yearAgo.minusYears(1)..yearAgo, ranges[1])
        assertEquals(fiveYearsAgo, ranges.last().start)
        assertEquals(5, ranges.size)
    }

    @Test
    fun `a span of one day is one range`() {
        assertEquals(listOf(today..today), timelineFetchRanges(today, null, null, today))
    }

    @Test
    fun `periods start where they say`() {
        assertEquals(today.minusWeeks(1), Period.WEEK.startDate(today))
        assertEquals(today.minusYears(5), Period.FIVE_YEARS.startDate(today))
        assertNull(Period.CUSTOM.startDate(today))
    }
}
