package com.eliormachlev.currencix.view.timeline.compose

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.AbstractMap.SimpleImmutableEntry

// What TalkBack reads for the chart.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS")
class TimelineSummaryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val dates = DateTimeFormatter.ofPattern("dd/MM/yy")

    private fun euro(value: String) = Rate(Currency.EUR, BigDecimal(value))

    private fun day(
        date: String,
        rate: String,
    ) = SimpleImmutableEntry<LocalDate, Rate?>(LocalDate.parse(date), euro(rate)) to 4

    private val stats =
        TimelineStats(
            ratePast = day("2025-09-13", "0.9153"),
            rateCurrent = day("2026-09-13", "0.9370"),
            diffPercent = BigDecimal("2.3708"),
            ratesMax = Triple(euro("0.9521"), LocalDate.parse("2026-03-04"), 4),
            ratesMin = Triple(euro("0.8998"), LocalDate.parse("2025-11-22"), 4),
        )

    @Test
    fun `change, first and last rate, high and low`() {
        assertEquals(
            "Up 2.37%, from 0.9153 Euro on 13/09/25 to 0.9370 Euro on 13/09/26, " +
                "high 0.9521 Euro on 04/03/26, low 0.8998 Euro on 22/11/25",
            timelineSummary(context, stats, dates),
        )
    }

    @Test
    fun `a fall and no change read as such`() {
        val down = timelineSummary(context, stats.copy(diffPercent = BigDecimal("-0.404")), dates)
        assertEquals(true, down?.startsWith("Down 0.40%, "))
        val flat = timelineSummary(context, stats.copy(diffPercent = BigDecimal("0.001")), dates)
        assertEquals(true, flat?.startsWith("Unchanged, "))
    }

    @Test
    fun `nothing before there's a first and last rate`() {
        assertNull(timelineSummary(context, TimelineStats(), dates))
    }
}
