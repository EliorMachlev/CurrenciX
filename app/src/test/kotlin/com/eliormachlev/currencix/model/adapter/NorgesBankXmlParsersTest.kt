package com.eliormachlev.currencix.model.adapter

import android.app.Application
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

private val DAY = LocalDate.of(2024, 3, 15)
private val PREVIOUS_DAY = LocalDate.of(2024, 3, 14)
private val MATH = MathContext.DECIMAL128

// Norges Bank's SDMX-ML: a series per currency (UNIT_MULT the power of ten its value is quoted in), its observations dated.
private fun feed(vararg series: String) =
    """<message:StructureSpecificData xmlns:message="urn:sdmx"><message:DataSet>${series.joinToString(
        "",
    )}</message:DataSet></message:StructureSpecificData>"""

private fun series(
    currency: String,
    unitMultiplier: Int,
    vararg observations: Pair<LocalDate, String>,
) = """<Series BASE_CUR="$currency" QUOTE_CUR="NOK" UNIT_MULT="$unitMultiplier">""" +
    observations.joinToString("") { (date, value) -> """<Obs TIME_PERIOD="$date" OBS_VALUE="$value"/>""" } +
    "</Series>"

// What one NOK buys: 1 / the quoted value, times the unit multiplier.
private fun perNok(
    quoted: String,
    multiplier: Int = 1,
) = BigDecimal.ONE.divide(BigDecimal(quoted), MATH) * multiplier.toBigDecimal()

private fun ExchangeRates.rate(currency: Currency): BigDecimal? = rates?.firstOrNull { it.currency == currency }?.value

private fun assertSame(
    expected: BigDecimal,
    actual: BigDecimal?,
) = assertEquals("$expected vs $actual", 0, expected.compareTo(checkNotNull(actual)))

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NorgesBankRatesXmlParserTest {
    private fun parse(
        xml: String,
        requestedDate: LocalDate = DAY,
    ) = NorgesBankRatesXmlParser().parse(xml.byteInputStream(), requestedDate)

    @Test
    fun `each currency comes out per krone, scaled by its unit multiplier`() {
        val rates = parse(feed(series("USD", 0, DAY to "10.5"), series("JPY", 2, DAY to "7.0")))

        assertTrue(rates.success == true)
        assertEquals(Currency.NOK, rates.base)
        assertEquals(ApiProvider.NORGES_BANK, rates.provider)
        assertEquals(DAY, rates.date)
        assertSame(perNok("10.5"), rates.rate(Currency.USD))
        assertSame(perNok("7.0", multiplier = 100), rates.rate(Currency.JPY))
        assertSame(BigDecimal.ONE, rates.rate(Currency.NOK))
    }

    @Test
    fun `the date is the latest one in the feed`() {
        val rates = parse(feed(series("USD", 0, PREVIOUS_DAY to "10.5"), series("EUR", 0, DAY to "11.7")))
        assertEquals(DAY, rates.date)
    }

    @Test
    fun `a rate more than two weeks older than the day asked for is left out`() {
        val stale = DAY.minusWeeks(2)
        val rates = parse(feed(series("RUB", 0, stale to "0.11"), series("USD", 0, DAY to "10.5")))

        assertNull(rates.rate(Currency.RUB))
        assertSame(perNok("10.5"), rates.rate(Currency.USD))
    }

    @Test
    fun `an observation with no value is skipped`() {
        val rates = parse(feed(series("USD", 0, DAY to ""), series("EUR", 0, DAY to "11.7")))

        assertNull(rates.rate(Currency.USD))
        assertSame(perNok("11.7"), rates.rate(Currency.EUR))
    }

    @Test
    fun `the Faroese króna follows the Danish krone when the feed has no rate for it`() {
        val rates = parse(feed(series("DKK", 0, DAY to "1.5")))
        assertSame(perNok("1.5"), rates.rate(Currency.FOK))
    }

    @Test
    fun `a feed with no observations is not a success`() {
        val rates = parse(feed())

        assertFalse(rates.success == true)
        assertEquals(emptyList<Any>(), rates.rates)
        assertNull(rates.date)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NorgesBankTimelineXmlParserTest {
    private fun parse(
        xml: String,
        base: Currency = Currency.USD,
        symbol: Currency = Currency.EUR,
    ) = NorgesBankTimelineXmlParser(base, symbol, PREVIOUS_DAY, DAY).parse(xml.byteInputStream())

    @Test
    fun `the symbol per base comes out for each day both have a rate`() {
        val timeline =
            parse(
                feed(
                    series("USD", 0, PREVIOUS_DAY to "10.0", DAY to "10.5"),
                    series("EUR", 0, PREVIOUS_DAY to "11.0", DAY to "11.5"),
                ),
            )

        assertTrue(timeline.success == true)
        assertEquals(ApiProvider.NORGES_BANK, timeline.provider)
        assertEquals("USD", timeline.base)
        assertEquals(PREVIOUS_DAY, timeline.startDate)
        assertEquals(DAY, timeline.endDate)
        // EUR per USD = (EUR per NOK) / (USD per NOK) = USD in NOK / EUR in NOK.
        assertSame(perNok("11.0").divide(perNok("10.0"), MATH), timeline.rates?.get(PREVIOUS_DAY)?.value)
        assertSame(perNok("11.5").divide(perNok("10.5"), MATH), timeline.rates?.get(DAY)?.value)
    }

    @Test
    fun `a day only one of the two has a rate for is left out`() {
        val timeline =
            parse(
                feed(
                    series("USD", 0, PREVIOUS_DAY to "10.0", DAY to "10.5"),
                    series("EUR", 0, DAY to "11.5"),
                ),
            )

        assertEquals(listOf(DAY), timeline.rates?.keys?.toList())
    }

    @Test
    fun `the unit multiplier is applied to each series`() {
        val timeline = parse(feed(series("USD", 0, DAY to "10.0"), series("JPY", 2, DAY to "7.0")), symbol = Currency.JPY)
        assertSame(perNok("7.0", multiplier = 100).divide(perNok("10.0"), MATH), timeline.rates?.get(DAY)?.value)
    }

    @Test
    fun `the krone is a base or symbol without a series of its own`() {
        val timeline = parse(feed(series("USD", 0, PREVIOUS_DAY to "10.0", DAY to "10.5")), base = Currency.NOK, symbol = Currency.USD)

        assertSame(perNok("10.0"), timeline.rates?.get(PREVIOUS_DAY)?.value)
        assertSame(perNok("10.5"), timeline.rates?.get(DAY)?.value)
    }

    @Test
    fun `a pair the feed has no series for is not a success`() {
        val timeline = parse(feed(series("USD", 0, DAY to "10.5")))

        assertFalse(timeline.success == true)
        assertEquals(emptyMap<LocalDate, Any>(), timeline.rates)
    }
}
