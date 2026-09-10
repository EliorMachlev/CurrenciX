package com.eliormachlev.currencix.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.view.cart.compose.CartEmptyHint
import com.eliormachlev.currencix.view.cart.compose.CartItemRow
import androidx.compose.ui.Modifier
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Cart surfaces: empty-cart hint plus a 3-row list rendered from
// CartItemRow directly (skips the VM-driven CartItemsList / CartScreen
// wrappers). Covers the "items" and "empty" and "no-price items"
// variants; "offline" and "history" are MainScreen-level banners and
// are already covered by MainScreenScreenshotTest.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class CartScreenshotTest {
    @Test fun cartEmpty() =
        captureMatrix("cart_empty") { CartEmptyHint() }

    @Test fun cartItems() =
        captureMatrix("cart_items") {
            Column(Modifier.fillMaxWidth()) {
                SAMPLE_ITEMS.forEach { CartRowPreview(it, currency = "USD") }
            }
        }

    @Test fun cartNoPriceItems() =
        captureMatrix("cart_no_price_items") {
            Column(Modifier.fillMaxWidth()) {
                NO_PRICE_ITEMS.forEach { CartRowPreview(it, currency = "USD") }
            }
        }

    companion object {
        // Named + priced rows the user would recognize as a grocery cart.
        private val SAMPLE_ITEMS =
            listOf(
                CartItem(id = "1", name = "Coffee", expression = "4.50", pinned = false),
                CartItem(id = "2", name = "Bread", expression = "3.25 + 0.50", pinned = true),
                CartItem(id = "3", name = "Milk", expression = "2.99 × 2", pinned = false),
            )

        // Rows the user hasn't filled in a price for — expression column blank.
        private val NO_PRICE_ITEMS =
            listOf(
                CartItem(id = "1", name = "Avocados", expression = "", pinned = false),
                CartItem(id = "2", name = "Cheese", expression = "", pinned = false),
            )
    }
}

@androidx.compose.runtime.Composable
private fun CartRowPreview(
    item: CartItem,
    currency: String,
) {
    CartItemRow(
        item = item,
        currency = currency,
        isActive = false,
        keyListener = null,
        liveExpression = null,
        onNameCommit = {},
        onNamePending = {},
        onExpressionTap = {},
        onExpressionChange = {},
        onTogglePin = {},
    )
}
