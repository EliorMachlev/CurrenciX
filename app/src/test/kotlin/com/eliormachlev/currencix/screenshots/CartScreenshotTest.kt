package com.eliormachlev.currencix.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.view.cart.compose.AddItemRow
import com.eliormachlev.currencix.view.cart.compose.CartEmptyState
import com.eliormachlev.currencix.view.cart.compose.CartItemRow
import com.eliormachlev.currencix.view.cart.compose.CartRowState
import com.eliormachlev.currencix.view.cart.compose.NO_ROW_ACTIONS
import com.eliormachlev.currencix.view.cart.compose.RowPosition
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.viewmodel.cart.RowConversion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

// Cart surfaces: empty-cart hint plus a 3-row list rendered from
// CartItemRow directly (skips the VM-driven CartItemsList / CartScreen
// wrappers). Covers the "items" and "empty" and "no-price items"
// variants; "offline" and "history" are MainScreen-level banners and
// are already covered by MainScreenScreenshotTest.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class CartScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    @Test fun cartEmpty() = shots.captureMatrix("cart_empty") { CartEmptyState(onAddItem = {}) }

    @Test fun cartItems() =
        shots.captureMatrix("cart_items") {
            Column(Modifier.fillMaxWidth()) {
                CartSectionPreview(SAMPLE_ITEMS)
            }
        }

    @Test fun cartItemsLargeFont() =
        shots.captureLargeFont("cart_items") {
            Column(Modifier.fillMaxWidth()) {
                CartSectionPreview(SAMPLE_ITEMS)
            }
        }

    @Test fun cartNoPriceItems() =
        shots.captureMatrix("cart_no_price_items") {
            Column(Modifier.fillMaxWidth()) {
                CartSectionPreview(NO_PRICE_ITEMS)
            }
        }

    // Shopping-list mode: ticks instead of drag handles, a ticked row crossed
    // off, a quantity, and a long name wrapping.
    @Test fun cartShoppingList() =
        shots.captureMatrix("cart_shopping_list") {
            Column(Modifier.fillMaxWidth()) {
                CartSectionPreview(SHOPPING_ITEMS, shoppingMode = true, editFirst = false)
            }
        }

    companion object {
        private val SHOPPING_ITEMS =
            listOf(
                CartItem(id = "1", name = "Coffee", expression = "4.50", checked = true),
                CartItem(id = "2", name = "Yogurt", expression = "1.20", quantity = 4),
                CartItem(id = "3", name = "Organic free-range eggs, large, from the farm stand", expression = "6.80"),
            )

        // Named + priced rows the user would recognize as a grocery cart.
        private val SAMPLE_ITEMS =
            listOf(
                CartItem(id = "1", name = "Coffee", expression = "4.50", pinned = false),
                CartItem(id = "2", name = "Bread", expression = "3.25 + 0.50", pinned = true),
                CartItem(id = "3", name = "Milk", expression = "2.99 × 2", pinned = false),
                // A long calculation wraps under the name; the result stays on the first line.
                CartItem(
                    id = "4",
                    name = "Weekly groceries",
                    expression = "12.90 + 4.50 × 3 + 2.25 + 7.80 − 1.50 + 3.10 + 0.99 × 4",
                    pinned = false,
                ),
            )

        // Rows the user hasn't filled in a price for — expression column blank.
        private val NO_PRICE_ITEMS =
            listOf(
                CartItem(id = "1", name = "Avocados", expression = "", pinned = false),
                CartItem(id = "2", name = "Cheese", expression = "", pinned = false),
            )
    }
}

// USD priced, shown in shekels, at a fixed sample rate.
private val TO_ILS = RowConversion(Currency.ILS) { it.multiply(BigDecimal("3.65")) }

// One section's card of rows, laid out the way the language reads, as
// CartScreen does (ProseTheme); the first row is the one being edited
// unless [editFirst] is off.
@androidx.compose.runtime.Composable
private fun CartSectionPreview(
    items: List<CartItem>,
    shoppingMode: Boolean = false,
    editFirst: Boolean = true,
) {
    ReadingDirection {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.Large, vertical = Spacing.Small)) {
            items.forEachIndexed { index, item ->
                CartItemRow(
                    state =
                        CartRowState(
                            item = item,
                            currency = "USD",
                            liveExpression = item.expression.takeIf { editFirst && index == 0 },
                            conversion = TO_ILS,
                            shoppingMode = shoppingMode,
                        ),
                    actions = NO_ROW_ACTIONS,
                    position = RowPosition.of(index, items.size),
                )
            }
            AddItemRow(onAddItem = {})
        }
    }
}
