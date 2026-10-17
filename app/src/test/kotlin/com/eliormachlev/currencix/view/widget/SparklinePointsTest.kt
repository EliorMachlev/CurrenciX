package com.eliormachlev.currencix.view.widget

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SparklinePointsTest {
    private val today = LocalDate.of(2026, 9, 29)

    private fun timeline(vararg daysAgo: Long) =
        Timeline(
            success = true,
            error = null,
            base = "USD",
            startDate = null,
            endDate = null,
            rates = daysAgo.associate { today.minusDays(it) to Rate(Currency.ILS, BigDecimal(100 - it)) },
        )

    @Test
    fun `only the last 30 days, oldest first`() {
        val points = sparklinePoints(timeline(0, 1, 15, 30, 31, 200), today)
        assertEquals(listOf("70", "85", "99", "100").map(::BigDecimal), points)
    }

    @Test
    fun `too little history draws nothing`() {
        assertTrue(sparklinePoints(timeline(0), today).isEmpty())
        assertTrue(sparklinePoints(null, today).isEmpty())
    }
}
