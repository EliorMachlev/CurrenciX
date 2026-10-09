package com.eliormachlev.currencix.view.main

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.util.registerActivityRule
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.main.compose.RecentPairsRow
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerContent
import com.eliormachlev.currencix.view.main.spinner.SearchableCurrencyPicker
import com.eliormachlev.currencix.view.main.spinner.pickerActions
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

// A long-press on a recent pair (under the hero card) or a recent currency
// (in the picker) asks before forgetting it: Delete removes, Cancel doesn't.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecentHistoryRemovalTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val removedPairs = mutableListOf<CurrencyPair>()
    private val removedCurrencies = mutableListOf<Currency>()

    @Test
    fun `delete forgets the long-pressed pair`() {
        show { PairsRow() }
        longPress(app.getString(R.string.recent_pair_switch, "EUR", "GBP"))
        compose.onNodeWithText(app.getString(R.string.recent_remove_pair, "EUR", "GBP")).assertExists()

        tap(app.getString(R.string.a11y_delete))

        assertEquals(listOf(EUR_GBP), removedPairs)
        // The sheet shows its title in small caps.
        compose.onNodeWithText(app.getString(R.string.recent_remove_title), ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `cancel keeps the long-pressed pair`() {
        show { PairsRow() }
        longPress(app.getString(R.string.recent_pair_switch, "EUR", "GBP"))
        compose.onNodeWithText(app.getString(R.string.recent_remove_title), ignoreCase = true).assertExists()

        tap(app.getString(android.R.string.cancel))

        assertEquals(emptyList<CurrencyPair>(), removedPairs)
        // The sheet shows its title in small caps.
        compose.onNodeWithText(app.getString(R.string.recent_remove_title), ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `delete forgets the long-pressed currency in the picker`() {
        show { Picker() }
        longPress(Currency.ILS.fullName(app))
        compose.onNodeWithText(app.getString(R.string.recent_remove_currency, "ILS")).assertExists()

        tap(app.getString(R.string.a11y_delete))

        assertEquals(listOf(Currency.ILS), removedCurrencies)
    }

    @Composable
    private fun PairsRow() =
        RecentPairsRow(
            pairs = persistentListOf(CurrencyPair(Currency.USD, Currency.ILS), EUR_GBP),
            onPick = {},
            onRemove = { removedPairs += it },
            contentPadding = PaddingValues(),
        )

    @Composable
    private fun Picker() =
        SearchableCurrencyPicker(
            content =
                CurrencyPickerContent(
                    rates = persistentListOf(Rate(Currency.USD, BigDecimal.ONE), Rate(Currency.ILS, BigDecimal("3.7"))),
                    stars = persistentListOf(),
                    recents = persistentListOf(Currency.ILS),
                ),
            actions = pickerActions(onRemoveRecent = { removedCurrencies += it }),
        )

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppTheme(content = content) }
        settle()
    }

    // The chip is the one node carrying [description] that can be long-pressed
    // (the picker's list row for the same currency only clicks).
    private fun longPress(description: String) {
        compose
            .onNodeWithContentDescription(description)
            .performSemanticsAction(SemanticsActions.OnLongClick)
        settle()
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
        val EUR_GBP = CurrencyPair(Currency.EUR, Currency.GBP)
    }
}
