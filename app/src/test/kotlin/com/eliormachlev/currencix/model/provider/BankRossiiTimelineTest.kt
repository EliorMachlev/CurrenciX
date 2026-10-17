package com.eliormachlev.currencix.model.provider

import android.app.Application
import com.eliormachlev.currencix.model.Currency
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

private val START: LocalDate = LocalDate.of(2024, 3, 1)
private val END: LocalDate = LocalDate.of(2024, 3, 5)

private const val IDS_XML =
    """<Valuta><Item ID="R01235"><ISO_Char_Code>USD</ISO_Char_Code></Item>""" +
        """<Item ID="R01239"><ISO_Char_Code>EUR</ISO_Char_Code></Item></Valuta>"""

private fun series(
    id: String,
    vararg rublesByDay: Pair<String, String>,
) = rublesByDay.joinToString("", "<ValCurs>", "</ValCurs>") { (day, rubles) ->
    """<Record Date="$day" Id="$id"><Nominal>1</Nominal><Value>$rubles</Value><VunitRate>$rubles</VunitRate></Record>"""
}

/**
 * The Bank of Russia quotes everything in RUB, so a timeline is two series
 * divided day by day. Robolectric supplies the XML pull parser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BankRossiiTimelineTest {
    @get:Rule val http = StubHttpRule()

    private fun stubSeries() {
        http.bodyByUrlPart["XML_valFull.asp"] = IDS_XML
        http.bodyByUrlPart["VAL_NM_RQ=R01235"] = series("R01235", "01.03.2024" to "90,0", "02.03.2024" to "80,0")
        http.bodyByUrlPart["VAL_NM_RQ=R01239"] = series("R01239", "02.03.2024" to "100,0", "05.03.2024" to "99,0")
    }

    @Test
    fun `the symbol is priced in the base on the days both were quoted`() =
        runBlocking {
            stubSeries()
            val timeline = BankRossii().getTimeline(null, Currency.USD, Currency.EUR, START, END).getOrThrow()

            // 2 March: 1 USD = 80 RUB, 1 EUR = 100 RUB, so 1 USD = 0.8 EUR.
            assertEquals(setOf(LocalDate.of(2024, 3, 2)), timeline.rates?.keys)
            val rate = timeline.rates?.values?.single()
            assertEquals(Currency.EUR, rate?.currency)
            assertEquals(0, BigDecimal("0.8").compareTo(rate?.value))
        }

    @Test
    fun `the ruble side needs no series of its own`() =
        runBlocking {
            stubSeries()
            val timeline = BankRossii().getTimeline(null, Currency.RUB, Currency.USD, START, END).getOrThrow()

            assertEquals(2, timeline.rates?.size)
            assertEquals(1, http.requests.count { "XML_dynamic.asp" in it.toString() })
        }

    @Test
    fun `a currency the bank doesn't list fails before any series is fetched`() =
        runBlocking {
            stubSeries()
            val result = BankRossii().getTimeline(null, Currency.USD, Currency.GBP, START, END)

            assertEquals("No currency ID found for: GBP", result.exceptionOrNull()?.message)
            assertTrue(http.requests.none { "XML_dynamic.asp" in it.toString() })
        }

    @Test
    fun `a range with no quotes is an empty timeline, not a crash`() =
        runBlocking {
            stubSeries()
            http.bodyByUrlPart["VAL_NM_RQ=R01239"] = "<ValCurs></ValCurs>"
            val timeline = BankRossii().getTimeline(null, Currency.USD, Currency.EUR, START, END).getOrThrow()

            assertEquals(emptyMap<LocalDate, Any>(), timeline.rates)
        }
}
