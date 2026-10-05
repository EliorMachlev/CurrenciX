package com.eliormachlev.currencix.model.provider

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.rateFor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

private val START: LocalDate = LocalDate.of(2024, 1, 1)
private val END: LocalDate = LocalDate.of(2024, 3, 31)

/**
 * What each JSON provider makes of a realistic response body — the adapters
 * behind [ProviderRequestTest]'s requests. Each adapter is registered for the
 * type it decodes, so a wrong registration shows up here as a failed decode.
 */
class ProviderDecodingTest {
    @get:Rule val http = StubHttpRule()

    private fun ExchangeRates.valueOf(currency: Currency): BigDecimal? = rateFor(currency)?.value

    @Test
    fun `frankfurter rates decode the rate map, the date, and add the base`() =
        runBlocking {
            http.body = """{"amount":1.0,"base":"EUR","date":"2024-03-15","rates":{"USD":1.0892,"DKK":7.4571}}"""
            val rates = FrankfurterApp().getRates(null, null, ApiSecrets.EMPTY).getOrThrow()

            assertEquals(Currency.EUR, rates.base)
            assertEquals(LocalDate.of(2024, 3, 15), rates.date)
            assertEquals(BigDecimal("1.0892"), rates.valueOf(Currency.USD))
            assertEquals("the base is added at 1", BigDecimal.ONE, rates.valueOf(Currency.EUR))
            assertEquals("FOK rides on DKK", BigDecimal("7.4571"), rates.valueOf(Currency.FOK))
        }

    @Test
    fun `frankfurter timeline keeps one rate per day, for the requested symbol`() =
        runBlocking {
            http.body =
                """{"amount":1.0,"base":"EUR","start_date":"2024-01-02","end_date":"2024-01-04",""" +
                """"rates":{"2024-01-02":{"USD":1.0956},"2024-01-03":{},"2024-01-04":{"USD":1.0919}}}"""
            val timeline = FrankfurterApp().getTimeline(null, Currency.EUR, Currency.USD, START, END).getOrThrow()

            assertEquals(LocalDate.of(2024, 1, 2), timeline.startDate)
            assertEquals(LocalDate.of(2024, 1, 4), timeline.endDate)
            assertEquals(
                mapOf(
                    LocalDate.of(2024, 1, 2) to Rate(Currency.USD, BigDecimal("1.0956")),
                    LocalDate.of(2024, 1, 4) to Rate(Currency.USD, BigDecimal("1.0919")),
                ),
                timeline.rates,
            )
        }

    @Test
    fun `open exchange rates decode base, rates and the timestamp's date`() =
        runBlocking {
            http.body = """{"timestamp":1710504000,"base":"USD","rates":{"EUR":0.9181,"ILS":3.65}}"""
            val rates = OpenExchangerates().getRates(null, null, ApiSecrets("key")).getOrThrow()

            assertEquals(true, rates.success)
            assertEquals(Currency.USD, rates.base)
            assertEquals(BigDecimal("0.9181"), rates.valueOf(Currency.EUR))
            assertEquals(BigDecimal("3.65"), rates.valueOf(Currency.ILS))
            assertTrue("a date comes from the timestamp", rates.date != null)
        }

    @Test
    fun `inforeuro rates decode the monthly array`() =
        runBlocking {
            http.body = """[{"isoA3Code":"USD","value":1.0834,"country":"US"},{"isoA3Code":"GBP","value":0.8553}]"""
            val rates = InforEuro().getRates(null, LocalDate.of(2024, 3, 15), ApiSecrets.EMPTY).getOrThrow()

            assertEquals(true, rates.success)
            assertEquals(Currency.EUR, rates.base)
            assertEquals(ApiProvider.INFOR_EURO, rates.provider)
            assertEquals(BigDecimal("1.0834"), rates.valueOf(Currency.USD))
            assertEquals(BigDecimal("0.8553"), rates.valueOf(Currency.GBP))
        }

    @Test
    fun `inforeuro reports its error message instead of rates`() =
        runBlocking {
            http.body = """{"message":"No rates for this month"}"""
            val rates = InforEuro().getRates(null, LocalDate.of(2024, 3, 15), ApiSecrets.EMPTY).getOrNull()

            assertEquals(false, rates?.success)
            assertNull(rates?.rates)
        }

    @Test
    fun `bank of canada rates decode an observation, inverted to CAD as base`() =
        runBlocking {
            http.body = """{"observations":[{"d":"2024-03-15","FXUSDCAD":{"v":"1.25"}}]}"""
            val rates = BankOfCanada().getRates(null, null, ApiSecrets.EMPTY).getOrThrow()

            assertEquals(Currency.CAD, rates.base)
            assertEquals(LocalDate.of(2024, 3, 15), rates.date)
            assertEquals(0, BigDecimal("0.8").compareTo(rates.valueOf(Currency.USD)))
            assertEquals(BigDecimal.ONE, rates.valueOf(Currency.CAD))
        }

    @Test
    fun `bank of canada timeline decodes every observation as symbol per base`() =
        runBlocking {
            http.body =
                """{"terms":{"url":"https://www.bankofcanada.ca/terms/"},"observations":[""" +
                """{"d":"2024-01-02","FXUSDCAD":{"v":"1.25"},"FXEURCAD":{"v":"1.5"}},""" +
                """{"d":"2024-01-03","FXUSDCAD":{"v":"1.2"},"FXEURCAD":{"v":"1.5"}}]}"""
            val timeline = BankOfCanada().getTimeline(null, Currency.USD, Currency.EUR, START, END).getOrThrow()

            assertEquals(LocalDate.of(2024, 1, 2), timeline.startDate)
            assertEquals(LocalDate.of(2024, 1, 3), timeline.endDate)
            assertEquals(2, timeline.rates?.size)
            assertEquals(
                "1 USD = 1.2 CAD, 1 EUR = 1.5 CAD",
                0,
                BigDecimal("0.8").compareTo(timeline.rates?.get(LocalDate.of(2024, 1, 3))?.value),
            )
        }

    @Test
    fun `bank of canada rates read past the fields around the observations`() =
        runBlocking {
            http.body =
                """{"terms":{"url":"https://www.bankofcanada.ca/terms/"},""" +
                """"observations":[{"d":"2024-03-15","FXUSDCAD":{"v":"1.25"}},{"d":"2024-03-14","FXUSDCAD":{"v":"1.3"}}],""" +
                """"seriesDetail":{}}"""
            val rates = BankOfCanada().getRates(null, null, ApiSecrets.EMPTY).getOrThrow()

            assertEquals(LocalDate.of(2024, 3, 15), rates.date)
            assertEquals(0, BigDecimal("0.8").compareTo(rates.valueOf(Currency.USD)))
        }

    @Test
    fun `inforeuro timeline spreads each monthly rate over its days, symbol per base`() =
        runBlocking {
            http.bodyByPathEnd["/GBP"] = """[{"currencyIso":"GBP","amount":0.88,"dateStart":"01/01/2024","dateEnd":"31/01/2024"}]"""
            http.bodyByPathEnd["/USD"] =
                """[{"currencyIso":"USD","amount":1.1,"dateStart":"01/01/2024","dateEnd":"31/01/2024"},""" +
                """{"currencyIso":"USD","amount":1.08,"dateStart":"01/02/2024","dateEnd":"29/02/2024"}]"""
            val timeline = InforEuro().getTimeline(null, Currency.GBP, Currency.USD, START, END).getOrThrow()

            assertEquals(0, BigDecimal("1.25").compareTo(timeline.rates?.get(LocalDate.of(2024, 1, 15))?.value))
            assertEquals(
                "a month without a base rate is taken as 1",
                BigDecimal("1.08"),
                timeline.rates?.get(LocalDate.of(2024, 2, 29))?.value,
            )
        }

    @Test
    fun `bank of israel rates decode per-unit values, inverted to ILS as base`() =
        runBlocking {
            http.body =
                """{"exchangeRates":[""" +
                """{"key":"USD","currentExchangeRate":4.0,"currentChange":0.1,"unit":1,"lastUpdate":"2024-03-15T13:22:00.0000000Z"},""" +
                """{"key":"JPY","currentExchangeRate":2.5,"currentChange":0.0,"unit":100,"lastUpdate":"2024-03-14T13:22:00.0000000Z"}]}"""
            val rates = BankOfIsrael().getRates(null, null, ApiSecrets.EMPTY).getOrThrow()

            assertEquals(Currency.ILS, rates.base)
            assertEquals(LocalDate.of(2024, 3, 15), rates.date)
            assertEquals(0, BigDecimal("0.25").compareTo(rates.valueOf(Currency.USD)))
            assertEquals(0, BigDecimal("40").compareTo(rates.valueOf(Currency.JPY)))
            assertEquals(BigDecimal.ONE, rates.valueOf(Currency.ILS))
        }
}
