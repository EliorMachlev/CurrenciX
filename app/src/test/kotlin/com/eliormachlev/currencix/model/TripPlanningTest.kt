package com.eliormachlev.currencix.model

import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.ocr.forCurrency
import com.eliormachlev.currencix.repository.throughJson
import com.eliormachlev.currencix.repository.throughProto
import com.eliormachlev.currencix.repository.withPlan
import com.eliormachlev.currencix.view.cart.tripOf
import com.eliormachlev.currencix.view.timeline.compose.percentFromAverage
import com.eliormachlev.currencix.viewmodel.cart.LanguageCheck
import com.eliormachlev.currencix.viewmodel.cart.RatesCheck
import com.eliormachlev.currencix.viewmodel.cart.tripChecklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

// A trip with where and when, and what the app does with them: per-day
// spending, its receipt language, the checklist, and noticing being abroad.
class TripPlanningTest {
    private val start = LocalDate.of(2026, 11, 7)
    private val end = LocalDate.of(2026, 11, 14)
    private val korea = Trip("t", "Korea", Currency.ILS, BigDecimal("8000"), Currency.KRW, start, end)

    @Test
    fun `a trip's days, the days so far, and when it's over`() {
        assertEquals(8L, korea.days)
        assertNull("not started", korea.daysSoFar(start.minusDays(1)))
        assertEquals(3L, korea.daysSoFar(start.plusDays(2)))
        assertEquals("over: all of it", 8L, korea.daysSoFar(end.plusDays(30)))
        assertFalse(korea.hasEnded(end))
        assertTrue(korea.hasEnded(end.plusDays(1)))
    }

    @Test
    fun `where and when survive both formats, and bad plans are fixed`() {
        assertEquals(korea, korea.throughProto())
        assertEquals(korea, korea.throughJson())
        val plain = Trip("p", "", Currency.EUR)
        assertEquals(plain, plain.throughProto())
        val messy = plain.withPlan(destinationCode = "EUR", start = end, end = start)
        assertNull("home isn't a destination", messy.destination)
        assertNull("an end before the start is dropped", messy.end)
    }

    @Test
    fun `a cart's trip is the one it's in, else one under way where it's priced`() {
        val cart = SavedCart("c", "", "KRW", emptyList(), 0)
        assertEquals(korea, tripOf(cart, listOf(korea), start))
        assertNull("over", tripOf(cart, listOf(korea), end.plusDays(1)))
        assertEquals(korea, tripOf(cart.copy(currency = "EUR", tripId = "t"), listOf(korea), end.plusDays(9)))
    }

    @Test
    fun `receipt languages follow the currency where it's one language`() {
        assertEquals(TessLanguage.KOREAN, TessLanguage.forCurrency(Currency.KRW))
        assertEquals(TessLanguage.JAPANESE, TessLanguage.forCurrency(Currency.JPY))
        assertNull("several languages", TessLanguage.forCurrency(Currency.EUR))
        assertNull("English is enough", TessLanguage.forCurrency(Currency.USD))
    }

    @Test
    fun `the checklist says what's missing`() {
        val rates =
            ExchangeRates(
                true,
                null,
                Currency.EUR,
                start.minusDays(5),
                listOf(Rate(Currency.KRW, BigDecimal("1500")), Rate(Currency.ILS, BigDecimal("4"))),
            )
        val card = Fee.GlobalBank("f", "Card", BigDecimal("2"))
        val missingLanguage = { language: TessLanguage -> LanguageCheck(language, installed = false, downloading = false) }
        val list = checkNotNull(tripChecklist(korea, rates, listOf(card), start, missingLanguage))
        assertEquals(RatesCheck.Old(start.minusDays(5)), list.rates)
        assertEquals(TessLanguage.KOREAN, list.language?.language)
        assertEquals(listOf(card), list.fees)
        assertFalse(list.ready)

        val fresh = rates.copy(date = start)
        val ready =
            checkNotNull(tripChecklist(korea, fresh, listOf(card), start) { LanguageCheck(it, installed = true, downloading = false) })
        assertTrue(ready.ready)
        assertEquals(RatesCheck.Missing, tripChecklist(korea, fresh.copy(rates = emptyList()), emptyList(), start, missingLanguage)?.rates)
        assertNull(
            "no destination, no checklist",
            tripChecklist(korea.copy(destination = null), fresh, emptyList(), start, missingLanguage),
        )
    }

    @Test
    fun `abroad, the local currency is offered once per visit`() {
        val currencyOf = mapOf("KR" to Currency.KRW, "IL" to Currency.ILS)::get
        val home = CurrencyPair(Currency.USD, Currency.ILS)
        assertEquals(AbroadOffer("KR", Currency.KRW, Currency.ILS), abroadOffer("kr", "IL", home, null, currencyOf))
        assertNull("asked this visit", abroadOffer("KR", "IL", home, "KR", currencyOf))
        assertNull("already showing it", abroadOffer("KR", "IL", CurrencyPair(Currency.KRW, Currency.ILS), null, currencyOf))
        assertNull("home", abroadOffer("IL", "IL", home, null, currencyOf))
        assertNull("no network", abroadOffer(null, "IL", home, null, currencyOf))
        assertTrue(isHome("il", "IL"))
        assertFalse(isHome(null, "IL"))
    }

    @Test
    fun `the chart's context is the latest rate against the average, in percent`() {
        assertEquals(0, BigDecimal("3.7").compareTo(percentFromAverage(BigDecimal("1.037"), BigDecimal("1"))))
        assertEquals(-1, checkNotNull(percentFromAverage(BigDecimal("0.9"), BigDecimal("1"))).signum())
        assertNull(percentFromAverage(null, BigDecimal.ONE))
        assertNull(percentFromAverage(BigDecimal.ONE, BigDecimal.ZERO))
    }
}
