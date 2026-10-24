package com.eliormachlev.currencix.repository.cache

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

// The rate caches on disk: what's written reads back whole, and files the
// org.json build wrote still read.
class RateConvertersTest {
    private val provider = ApiProvider.entries.first()

    @Test
    fun `exchange rates round-trip, with the time and provider`() {
        val rates =
            ExchangeRates(
                success = true,
                error = null,
                base = Currency.USD,
                date = LocalDate.of(2026, 10, 23),
                rates = listOf(Rate(Currency.EUR, BigDecimal("0.893400000001")), Rate(Currency.ILS, BigDecimal("3.71"))),
                time = LocalTime.of(14, 5, 9),
                provider = provider,
            )
        assertEquals(rates, ExchangeRatesConverter.decode(ExchangeRatesConverter.encode(rates)))
    }

    @Test
    fun `exchange rates written by the org-json build still read`() {
        val rates = """{"EUR":"0.8934","XXX_NOT_A_CURRENCY":"1","ILS":3.71}"""
        val old = """{"base":"USD","date":"2026-10-23","provider":${provider.id},"rates":$rates}"""
        val decoded = ExchangeRatesConverter.decode(old)
        assertEquals(provider, decoded?.provider)
        assertEquals(
            listOf(Currency.EUR to "0.8934", Currency.ILS to "3.71"),
            decoded?.rates?.map { it.currency to it.value.toPlainString() },
        )
        assertNull("no rates, nothing to show", ExchangeRatesConverter.decode("""{"rates":{}}"""))
        assertNull(ExchangeRatesConverter.decode("not json"))
    }

    @Test
    fun `a timeline round-trips in date order`() {
        val converter = TimelineConverter(Currency.EUR)
        val timeline =
            Timeline(
                success = true,
                error = null,
                base = "USD",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 1, 3),
                rates =
                    sortedMapOf(
                        LocalDate.of(2026, 1, 3) to Rate(Currency.EUR, BigDecimal("0.91")),
                        LocalDate.of(2026, 1, 1) to Rate(Currency.EUR, BigDecimal("0.9")),
                    ),
                provider = provider,
            )
        val decoded = converter.decode(converter.encode(timeline))
        assertEquals(timeline, decoded)
        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3)), decoded?.rates?.keys?.toList())
        assertNull("dates that don't parse are skipped", converter.decode("""{"rates":{"someday":"1"}}"""))
    }
}
