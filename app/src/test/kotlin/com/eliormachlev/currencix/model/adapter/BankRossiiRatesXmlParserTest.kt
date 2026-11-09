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

// Bank Rossii's daily list: roubles per one unit of each currency (VunitRate), a comma as the decimal point.
// No whitespace between the tags, as the feed has none.
private fun list(vararg currencies: Pair<String, String>) =
    """<ValCurs Date="15.03.2024" name="Foreign Currency Market">""" +
        currencies.joinToString("") { (code, unitRate) ->
            "<Valute><CharCode>$code</CharCode><Nominal>1</Nominal><Value>$unitRate</Value><VunitRate>$unitRate</VunitRate></Valute>"
        } +
        "</ValCurs>"

private fun ExchangeRates.rate(currency: Currency): BigDecimal? = rates?.firstOrNull { it.currency == currency }?.value

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BankRossiiRatesXmlParserTest {
    private fun parse(xml: String) = BankRossiiRatesXmlParser().parse(xml.byteInputStream())

    private fun perRouble(roubles: String) = BigDecimal.ONE.divide(BigDecimal(roubles), MathContext.DECIMAL128)

    @Test
    fun `each currency comes out per rouble, from the comma-decimal unit rate`() {
        val rates = parse(list("USD" to "91,9", "JPY" to "0,6125"))

        assertTrue(rates.success == true)
        assertEquals(Currency.RUB, rates.base)
        assertEquals(ApiProvider.BANK_ROSSII, rates.provider)
        assertEquals(LocalDate.of(2024, 3, 15), rates.date)
        assertEquals(0, perRouble("91.9").compareTo(rates.rate(Currency.USD)))
        assertEquals(0, perRouble("0.6125").compareTo(rates.rate(Currency.JPY)))
    }

    @Test
    fun `the rouble itself is one`() {
        assertEquals(0, BigDecimal.ONE.compareTo(parse(list("USD" to "91,9")).rate(Currency.RUB)))
    }

    @Test
    fun `the Faroese króna follows the Danish krone`() {
        val rates = parse(list("DKK" to "13,3"))
        assertEquals(0, perRouble("13.3").compareTo(rates.rate(Currency.FOK)))
    }

    @Test
    fun `a currency the app doesn't know is skipped, the rest stay`() {
        val rates = parse(list("XXY" to "5,0", "USD" to "91,9"))

        assertEquals(setOf(Currency.USD, Currency.RUB), rates.rates?.map { it.currency }?.toSet())
    }

    @Test
    fun `a list with no currencies is not a success`() {
        val rates = parse(list())

        assertFalse(rates.success == true)
        assertEquals(emptyList<Any>(), rates.rates)
        assertNull(rates.error)
    }
}
