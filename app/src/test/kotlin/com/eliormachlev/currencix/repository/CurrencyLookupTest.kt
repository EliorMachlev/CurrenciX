package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyLookupTest {
    @Test
    fun `every currency is found by its code`() {
        val excluded = setOf("BTC", "XAG", "XAU", "XPD", "XPT", "MRO", "STD", "VEF", "CUC", "XDR", "CLF", "CNH")
        Currency.entries.filterNot { it.iso4217Alpha() in excluded }.forEach { assertEquals(it, Currency.fromString(it.iso4217Alpha())) }
    }

    @Test
    fun `excluded and unknown codes find nothing`() {
        assertNull(Currency.fromString("XAU"))
        assertNull(Currency.fromString("BTC"))
        assertNull(Currency.fromString("usd"))
        assertNull(Currency.fromString(""))
    }
}
