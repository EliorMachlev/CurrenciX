package com.eliormachlev.currencix.model

import org.junit.Assert.assertEquals
import org.junit.Test

// The first-run pair: into the region's currency, from the dollar (the euro where the dollar is home).
class HomeCurrencyTest {
    @Test
    fun `a region's currency is the to side`() {
        assertEquals(CurrencyPair(Currency.USD, Currency.ILS), HomeCurrency.pairFor("IL"))
        assertEquals(CurrencyPair(Currency.USD, Currency.EUR), HomeCurrency.pairFor("DE"))
        assertEquals(CurrencyPair(Currency.USD, Currency.JPY), HomeCurrency.pairFor("JP"))
    }

    @Test
    fun `where the dollar is home, from the euro`() {
        assertEquals(CurrencyPair(Currency.EUR, Currency.USD), HomeCurrency.pairFor("US"))
    }

    @Test
    fun `no usable region falls back to dollar to euro`() {
        val fallback = CurrencyPair(Currency.USD, Currency.EUR)
        assertEquals(fallback, HomeCurrency.pairFor(null))
        assertEquals(fallback, HomeCurrency.pairFor(""))
        assertEquals(fallback, HomeCurrency.pairFor("not a region"))
        // Antarctica has no currency of its own.
        assertEquals(fallback, HomeCurrency.pairFor("AQ"))
    }
}
