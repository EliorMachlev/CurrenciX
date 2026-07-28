package de.salomax.currencies.repository

import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Timeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// Live-network tests hit real upstream APIs, which occasionally 5xx or drop
// HTTP/2 streams under CI-runner load. Retry once with a short backoff so a
// single upstream hiccup doesn't fail the whole build gate — a true outage
// still surfaces after both attempts.
private const val LIVE_RETRY_ATTEMPTS = 2
private const val LIVE_RETRY_BACKOFF_MS = 3_000L

private suspend fun <T> retryLive(block: suspend () -> T): T {
    var last: Throwable? = null
    repeat(LIVE_RETRY_ATTEMPTS) { attempt ->
        try {
            return block()
        } catch (t: Throwable) {
            last = t
            if (attempt < LIVE_RETRY_ATTEMPTS - 1) delay(LIVE_RETRY_BACKOFF_MS)
        }
    }
    throw last!!
}

class ExchangeRatesServiceTest {
    @Test
    fun testFrankfurterApp() =
        runBlocking {
            // latest
            testWebservice(
                retryLive { ExchangeRatesService.getRates(ApiProvider.FRANKFURTER_APP).getOrThrow() },
                4,
            )
            // timeline
            testTimeline(
                retryLive {
                    ExchangeRatesService
                        .getTimeline(
                            ApiProvider.FRANKFURTER_APP,
                            Currency.EUR,
                            Currency.ISK,
                        ).getOrThrow()
                },
            )
        }

//    @Test
//    fun testFerEe() = runBlocking {
//        // latest
//        testWebservice(
//            ExchangeRatesService.getRates(ApiProvider.FER_EE).getOrThrow(), 4
//        )
//        // timeline
//        testTimeline(
//            ExchangeRatesService.getTimeline(
//                ApiProvider.FER_EE,
//                Currency.EUR, Currency.ISK
//            ).getOrThrow()
//        )
//    }

    @Test
    fun testInforEuro() =
        runBlocking {
            // latest
            testWebservice(
                retryLive { ExchangeRatesService.getRates(ApiProvider.INFOR_EURO).getOrThrow() },
                31,
            )
            // timeline
            testTimeline(
                retryLive {
                    ExchangeRatesService
                        .getTimeline(
                            ApiProvider.INFOR_EURO,
                            Currency.EUR,
                            Currency.ISK,
                        ).getOrThrow()
                },
            )
        }

    /*
     * Can't unit test providers where XmlPullParserFactory is used. It's an Android component!
     */

//    @Test
//    fun testBankOfCanada() = runBlocking {
//        // latest
//        testWebservice(
//            ExchangeRatesService.getRates(ApiProvider.BANK_OF_CANADA).getOrThrow(), 4
//        )
//        // timeline
//        testTimeline(
//            ExchangeRatesService.getTimeline(
//                ApiProvider.BANK_OF_CANADA,
//                Currency.EUR, Currency.CAD
//            ).getOrThrow()
//        )
//    }
//
//    @Test
//    fun testBankRossii() = runBlocking {
//        // latest
//        testWebservice(
//            ExchangeRatesService.getRates(ApiProvider.BANK_ROSSII).getOrThrow(), 4
//        )
//        // timeline
//        testTimeline(
//            ExchangeRatesService.getTimeline(
//                ApiProvider.BANK_ROSSII,
//                Currency.EUR, Currency.RUB
//            ).getOrThrow()
//        )
//    }
//
//    @Test
//    fun testNorgesBank() = runBlocking {
//        // latest
//        testWebservice(
//            ExchangeRatesService.getRates(ApiProvider.NORGES_BANK).getOrThrow(), 4
//        )
//        // timeline
//        testTimeline(
//            ExchangeRatesService.getTimeline(
//                ApiProvider.NORGES_BANK,
//                Currency.EUR, Currency.NOK
//            ).getOrThrow()
//        )
//    }

    private fun testWebservice(
        rates: ExchangeRates?,
        maxAge: Long,
    ) {
        // see there is some valid data
        assertNotNull(rates)

        // see that there is a list of exchange rates
        assertNotNull(rates!!.rates)

        // check for some currencies
        val eur = rates.rates!!.find { rate -> rate.currency == Currency.EUR }
        assertTrue(eur != null)
        println(eur)

        val usd = rates.rates.find { rate -> rate.currency == Currency.USD }
        assertTrue(usd != null)
        println(usd)

        val jpy = rates.rates.find { rate -> rate.currency == Currency.JPY }
        assertTrue(jpy != null)
        println(jpy)

        val krw = rates.rates.find { rate -> rate.currency == Currency.KRW }
        assertTrue(krw != null)
        println(krw)

        val chf = rates.rates.find { rate -> rate.currency == Currency.CHF }
        assertTrue(chf != null)
        println(chf)

        // check if the date is current
        rates.date?.let {
            assertTrue(it >= LocalDate.now(ZoneId.of("UTC")).minusDays(maxAge))
            println(rates.date)
        }
    }

    private fun testTimeline(data: Timeline) {
        assertTrue(data.rates != null)
        assertTrue(data.rates!!.isNotEmpty())
    }
}
