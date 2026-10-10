package com.eliormachlev.currencix.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Removing a recent pair or currency hands back what Undo needs to put it
// back where it was.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecentPairsStoreTest {
    private val store = Database(ApplicationProvider.getApplicationContext()).lastState

    private fun recents() = store.getRecentPairsBlocking()

    private fun seed() {
        // The store outlives a test: start from an empty history.
        recents().forEach(store::removeRecentPair)
        // Pushed oldest first: the list reads newest first.
        listOf(GBP_JPY, EUR_USD, USD_ILS).forEach(store::addRecentPair)
    }

    @Test
    fun `undo puts a removed pair back in its place`() {
        seed()
        val before = store.removeRecentPair(EUR_USD)
        assertEquals(listOf(USD_ILS, GBP_JPY), recents())

        store.restoreRecentPairs(before)

        assertEquals(listOf(USD_ILS, EUR_USD, GBP_JPY), recents())
    }

    @Test
    fun `undo puts back every pair a removed currency took with it`() {
        seed()
        val before = store.removeRecentCurrency(Currency.USD)
        assertEquals(listOf(GBP_JPY), recents())

        store.restoreRecentPairs(before)

        assertEquals(listOf(USD_ILS, EUR_USD, GBP_JPY), recents())
    }

    private companion object {
        val USD_ILS = CurrencyPair(Currency.USD, Currency.ILS)
        val EUR_USD = CurrencyPair(Currency.EUR, Currency.USD)
        val GBP_JPY = CurrencyPair(Currency.GBP, Currency.JPY)
    }
}
