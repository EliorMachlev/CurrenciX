package com.eliormachlev.currencix.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentPairsTest {
    private val usdIls = CurrencyPair(Currency.USD, Currency.ILS)
    private val eurUsd = CurrencyPair(Currency.EUR, Currency.USD)
    private val gbpJpy = CurrencyPair(Currency.GBP, Currency.JPY)

    @Test
    fun `push puts the pair first and keeps each pair once`() {
        val recents = RecentPairs.push(RecentPairs.push(listOf(usdIls), eurUsd), usdIls)
        assertEquals(listOf(usdIls, eurUsd), recents)
    }

    @Test
    fun `a swapped pair replaces itself instead of taking a second slot`() {
        val swapped = CurrencyPair(Currency.ILS, Currency.USD)
        assertTrue(usdIls.isSameCurrencies(swapped))
        assertFalse(usdIls.isSameCurrencies(eurUsd))
        assertFalse(usdIls.isSameCurrencies(null))

        assertEquals(listOf(swapped, eurUsd), RecentPairs.push(listOf(usdIls, eurUsd), swapped))
    }

    @Test
    fun `push keeps at most MAX pairs, dropping the oldest`() {
        val all = Currency.entries.take(RecentPairs.MAX + 2).zipWithNext { a, b -> CurrencyPair(a, b) }
        val recents = all.fold(emptyList<CurrencyPair>(), RecentPairs::push)
        assertEquals(RecentPairs.MAX, recents.size)
        assertEquals(all.last(), recents.first())
    }

    @Test
    fun `encode and decode round trip`() {
        val recents = listOf(usdIls, eurUsd, gbpJpy)
        assertEquals("USD:ILS,EUR:USD,GBP:JPY", RecentPairs.encode(recents))
        assertEquals(recents, RecentPairs.decode(RecentPairs.encode(recents)))
    }

    @Test
    fun `decode drops what it can't read`() {
        assertEquals(emptyList<CurrencyPair>(), RecentPairs.decode(null))
        assertEquals(emptyList<CurrencyPair>(), RecentPairs.decode(""))
        assertEquals(listOf(usdIls), RecentPairs.decode("USD:ILS,XXX:USD,EUR,EUR:EUR,USD:ILS"))
    }

    @Test
    fun `currencies lists each currency once, most recent first`() {
        assertEquals(
            listOf(Currency.USD, Currency.ILS, Currency.EUR, Currency.GBP, Currency.JPY),
            RecentPairs.currencies(listOf(usdIls, eurUsd, gbpJpy)),
        )
    }
}
