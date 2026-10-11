package com.eliormachlev.currencix.view.tile

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

// The tile's label and rate.
class RateTileTest {
    private val rates =
        ExchangeRates(
            success = true,
            error = null,
            base = Currency.EUR,
            date = null,
            rates = listOf(Rate(Currency.USD, BigDecimal("1.1")), Rate(Currency.ILS, BigDecimal("4.0156"))),
        )

    @Test
    fun `label and rate for the pair`() {
        assertEquals("USD → ILS", tileLabel(Currency.USD, Currency.ILS))
        assertEquals(0, BigDecimal("3.6505").compareTo(tileRate(rates, Currency.USD, Currency.ILS)))
    }

    @Test
    fun `nothing without a pair or its rates`() {
        assertNull(tileLabel(null, Currency.ILS))
        assertNull(tileRate(null, Currency.USD, Currency.ILS))
        assertNull(tileRate(rates, Currency.USD, Currency.JPY))
    }
}
