package com.eliormachlev.currencix.view.a11y

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.MutableLiveData
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.util.registerActivityRule
import com.eliormachlev.currencix.view.cart.compose.CartExtrasSheet
import com.eliormachlev.currencix.view.cart.compose.CartItemsList
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.convert.ConvertTextSheet
import com.eliormachlev.currencix.view.convert.SelectionConversion
import com.eliormachlev.currencix.view.main.compose.MainKeypad
import com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks
import com.eliormachlev.currencix.view.main.compose.RecentPairsRow
import com.eliormachlev.currencix.view.main.spinner.SearchableCurrencyPicker
import com.eliormachlev.currencix.view.preference.compose.ProviderPickerDialog
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * What TalkBack needs from every screen: each thing you can tap says what it
 * is — a content description, its text, or its field's text. An unlabelled
 * button reads as just "button". Checked on the merged semantics tree, the
 * one TalkBack walks, across every window (sheets included).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScreenReaderLabelsTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    private fun assertEveryTapTargetIsLabelled(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppTheme(content = content) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val targets = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("nothing to tap was found", targets.isNotEmpty())
        val unlabelled = targets.filterNot(::isLabelled)
        assertTrue("unlabelled tap targets: ${unlabelled.map { it.config }}", unlabelled.isEmpty())
    }

    private fun isLabelled(node: SemanticsNode): Boolean {
        val config = node.config
        return !config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() ||
            config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.isNotBlank() } ||
            config.getOrNull(SemanticsProperties.EditableText)?.isNotBlank() == true
    }

    @Test fun keypad() =
        assertEveryTapTargetIsLabelled {
            MainKeypad(
                isExpandedKeypad = true,
                nextParen = '(',
                callbacks = MainKeypadCallbacks({}, {}, {}, {}, {}, {}, {}),
            )
        }

    @Test fun cartRows() =
        assertEveryTapTargetIsLabelled {
            CartItemsList(
                itemsSource = MutableLiveData(persistentListOf(CartItem("1", "Coffee", "4.50", pinned = true), CartItem("2", "", ""))),
                currencySource = MutableLiveData("USD"),
                activeItemIdSource = MutableLiveData(null),
                activeExpressionSource = MutableLiveData(""),
                onNameCommit = { _, _ -> },
                onNamePending = { _, _ -> },
                onExpressionTap = {},
                onTogglePin = {},
                onDelete = {},
                onReorder = { _, _ -> },
                onReorderStart = {},
                onBackgroundTap = {},
            )
        }

    @Test fun recentPairs() =
        assertEveryTapTargetIsLabelled {
            RecentPairsRow(
                pairs = persistentListOf(CurrencyPair(Currency.USD, Currency.ILS), CurrencyPair(Currency.EUR, Currency.GBP)),
                onPick = {},
                onRemove = {},
                contentPadding = PaddingValues(),
            )
        }

    @Test fun currencyPicker() =
        assertEveryTapTargetIsLabelled {
            SearchableCurrencyPicker(
                rates = persistentListOf(Rate(Currency.USD, BigDecimal.ONE), Rate(Currency.EUR, BigDecimal("0.9"))),
                stars = persistentListOf(Currency.EUR),
                filterStarred = false,
                conversion = null,
                disabledCurrency = null,
                onRateClicked = {},
                onStarClicked = {},
                onToggleStarredFilter = {},
                onStarredOrderChanged = {},
                recents = persistentListOf(Currency.ILS),
            )
        }

    @Test fun providerPicker() =
        assertEveryTapTargetIsLabelled {
            ProviderPickerDialog(
                selected = ApiProvider.FRANKFURTER_APP,
                onDismiss = {},
                onPicked = {},
                unavailable = ApiProvider.BANK_OF_ISRAEL,
            )
        }

    @Test fun cartExtras() =
        assertEveryTapTargetIsLabelled {
            CartExtrasSheet(initial = CartExtras(splitWays = 2), destination = Currency.ILS, onDone = {}, onDismiss = {})
        }

    @Test fun convertSelection() =
        assertEveryTapTargetIsLabelled {
            ConvertTextSheet(
                text = "€10",
                conversion =
                    SelectionConversion.Converted(
                        BigDecimal.TEN,
                        Currency.EUR,
                        Currency.ILS,
                        BigDecimal("36.8"),
                        BigDecimal("3.68"),
                        false,
                    ),
                decimals = 2,
                onOpen = {},
                onDismiss = {},
            )
        }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
    }
}
