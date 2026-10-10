package com.eliormachlev.currencix.screenshots

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.view.cart.compose.CartExtrasSheet
import com.eliormachlev.currencix.view.cart.compose.CartFooterCard
import com.eliormachlev.currencix.view.cart.compose.CartItemsList
import com.eliormachlev.currencix.view.cart.compose.CartTotals
import com.eliormachlev.currencix.view.cart.compose.NO_PAIR_ACTIONS
import com.eliormachlev.currencix.view.cart.compose.NO_REORDER
import com.eliormachlev.currencix.view.cart.compose.cartItemActions
import com.eliormachlev.currencix.view.cart.compose.cartSources
import com.eliormachlev.currencix.view.compose.PairChipContent
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.RemoveFromHistorySheet
import com.eliormachlev.currencix.view.convert.ConvertTextSheet
import com.eliormachlev.currencix.view.convert.SelectionConversion
import com.eliormachlev.currencix.view.main.compose.RecentPairsRow
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerContent
import com.eliormachlev.currencix.view.main.spinner.SearchableCurrencyPicker
import com.eliormachlev.currencix.view.main.spinner.pickerActions
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

// The surfaces added with recent pairs, cart sections and extras, the
// text-selection popup. (Wallpaper colors aren't here:
// Robolectric has no wallpaper palette, so they'd render as paper and ink.)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class NewSurfacesScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    // What a long-press on a recent pair opens.
    @Test fun removeFromHistorySheet() =
        shots.captureMatrix("remove_from_history_sheet") {
            RemoveFromHistorySheet(
                message = stringResource(R.string.recent_remove_pair, "SEK", "ILS"),
                onConfirm = {},
                onDismiss = {},
            ) { PairChipContent(Currency.SEK, Currency.ILS) }
        }

    @Test fun recentPairs() =
        shots.captureMatrix("recent_pairs") {
            RecentPairsRow(pairs = RECENT_PAIRS, onPick = {}, onRemove = {}, contentPadding = PaddingValues(16.dp))
        }

    @Test fun currencyPickerRecents() =
        shots.captureMatrix("currency_picker_recents") {
            SearchableCurrencyPicker(
                content =
                    CurrencyPickerContent(
                        rates = RATES,
                        stars = persistentListOf(Currency.EUR),
                        recents = persistentListOf(Currency.ILS, Currency.GBP, Currency.JPY),
                        disabledCurrency = Currency.USD,
                    ),
                actions = pickerActions(),
            )
        }

    @Test fun cartSections() =
        shots.captureMatrix("cart_sections") {
            // Laid out the way the language reads, as CartScreen does.
            ReadingDirection {
                CartItemsList(
                    sources = cartSources(MutableLiveData(CART_ITEMS)),
                    actions = cartItemActions(),
                    reorder = NO_REORDER,
                    onBackgroundTap = {},
                )
            }
        }

    @Test fun cartFooterExtras() =
        shots.captureMatrix("cart_footer_extras") {
            // Laid out the way the language reads, as CartScreen does.
            ReadingDirection {
                CartFooterCard(
                    totals =
                        CartTotals(
                            baseCurrency = Currency.USD,
                            destCurrency = Currency.ILS,
                            subtotal = BigDecimal("100"),
                            convertedSubtotal = BigDecimal("419.75"),
                            total = BigDecimal("427.15"),
                            feeStack = BigDecimal("1.0176"),
                            extras = CartExtras(tipPercent = BigDecimal("15"), splitWays = 3, budget = BigDecimal("400")),
                            tip = BigDecimal("15"),
                        ),
                    pair = NO_PAIR_ACTIONS,
                    onEditExtras = {},
                )
            }
        }

    @Test fun cartExtrasSheet() =
        shots.captureMatrix("cart_extras_sheet") {
            CartExtrasSheet(
                initial = CartExtras(tipPercent = BigDecimal("15"), splitWays = 3, budget = BigDecimal("400")),
                destination = Currency.ILS,
                onDone = {},
                onDismiss = {},
            )
        }

    @Test fun convertSelection() =
        shots.captureMatrix("convert_selection") {
            ConvertTextSheet(
                text = "€49.99",
                conversion =
                    SelectionConversion.Converted(
                        amount = BigDecimal("49.99"),
                        from = Currency.EUR,
                        to = Currency.ILS,
                        result = BigDecimal("184.21"),
                        rate = BigDecimal("3.6849"),
                        assumedFrom = false,
                    ),
                decimals = 2,
                onOpen = {},
                onDismiss = {},
            )
        }

    @Test fun convertSelectionLargeFont() =
        shots.captureLargeFont("convert_selection") {
            ConvertTextSheet(
                text = "€49.99",
                conversion =
                    SelectionConversion.Converted(
                        amount = BigDecimal("49.99"),
                        from = Currency.EUR,
                        to = Currency.ILS,
                        result = BigDecimal("184.21"),
                        rate = BigDecimal("3.6849"),
                        assumedFrom = false,
                    ),
                decimals = 2,
                onOpen = {},
                onDismiss = {},
            )
        }

    private companion object {
        val RECENT_PAIRS =
            persistentListOf(
                CurrencyPair(Currency.USD, Currency.ILS),
                CurrencyPair(Currency.EUR, Currency.GBP),
                CurrencyPair(Currency.JPY, Currency.USD),
            )
        val RATES =
            persistentListOf(
                Rate(Currency.USD, BigDecimal.ONE),
                Rate(Currency.EUR, BigDecimal("0.88")),
                Rate(Currency.GBP, BigDecimal("0.76")),
                Rate(Currency.ILS, BigDecimal("3.68")),
                Rate(Currency.JPY, BigDecimal("148.2")),
            )
        val CART_ITEMS =
            persistentListOf(
                CartItem(id = "1", name = "Coffee", expression = "4.50"),
                CartItem(id = "2", name = "Bread", expression = "3.25 + 0.50", pinned = true),
                CartItem(id = "3", name = "Milk", expression = "2.99 × 2"),
                CartItem(id = "4", name = "Olive oil", expression = "12.90", pinned = true),
            )
    }
}
