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
    fun `nothing cached fetches the whole span`() {
        assertEquals(yearAgo, timelineFetchStart(yearAgo, null, null))
    }

    @Test
    fun `a cache reaching back far enough fetches only the tail`() {
        val last = today.minusDays(2)
        assertEquals(last, timelineFetchStart(yearAgo, yearAgo, last))
        // Weekends: the first published day can be a few days after the start.
        assertEquals(last, timelineFetchStart(yearAgo, yearAgo.plusDays(3), last))
    }

    @Test
    fun `a longer span than the cache fetches it all again`() {
        val fiveYearsAgo = today.minusYears(5)
        assertEquals(fiveYearsAgo, timelineFetchStart(fiveYearsAgo, yearAgo, today))
    }

    @Test
    fun `periods start where they say`() {
        assertEquals(today.minusWeeks(1), Period.WEEK.startDate(today))
        assertEquals(today.minusYears(5), Period.FIVE_YEARS.startDate(today))
        assertNull(Period.CUSTOM.startDate(today))
    }
}
