package com.eliormachlev.currencix.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrencyCountriesTest {
    @Test
    fun `a currency is found by the countries that use it`() {
        val euro = CurrencyCountries.searchText(Currency.EUR, Locale.ENGLISH)
        assertTrue(euro.contains("germany"))
        assertTrue(euro.contains("france"))
        assertTrue(CurrencyCountries.searchText(Currency.JPY, Locale.ENGLISH).contains("japan"))
        assertFalse(CurrencyCountries.searchText(Currency.JPY, Locale.ENGLISH).contains("germany"))
    }

    @Test
    fun `names are in the app's language and in English`() {
        val euro = CurrencyCountries.searchText(Currency.EUR, Locale.GERMAN)
        assertTrue(euro.contains("deutschland"))
        assertTrue(euro.contains("germany"))
    }

    @Test
    fun `names are normalized for search`() {
        // Österreich → oesterreich / osterreich, depending on stripDiacritics — lowercase either way.
        val euro = CurrencyCountries.searchText(Currency.EUR, Locale.GERMAN)
        assertTrue(euro == euro.lowercase())
        assertFalse(euro.contains("ö"))
    }
}
