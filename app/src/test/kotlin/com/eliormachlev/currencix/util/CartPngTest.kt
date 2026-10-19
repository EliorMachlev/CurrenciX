package com.eliormachlev.currencix.util

import android.app.Application
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.viewmodel.cart.CartSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.LocalDateTime

// The receipt image: a real PNG, as wide as the PDF page, and as long as the cart.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class CartPngTest {
    private val labels = CartExportLabels.from(ApplicationProvider.getApplicationContext())

    private fun snapshot(itemCount: Int): CartSnapshot {
        val items = (1..itemCount).map { CartItem(it.toString(), "Item $it", "1") }
        val sum = BigDecimal(itemCount)
        return CartSnapshot(
            cart = SavedCart(id = "", name = "", currency = "USD", items = items, createdAt = 0),
            evaluatedItems = items.map { it to BigDecimal.ONE },
            subtotal = sum,
            tip = BigDecimal.ZERO,
            convertedSubtotal = sum,
            feeStack = BigDecimal.ONE,
            total = sum,
            fees = emptyList(),
            baseCurrency = Currency.USD,
            destinationCurrency = Currency.USD,
        )
    }

    private fun size(itemCount: Int): Pair<Int, Int> {
        val bytes = snapshot(itemCount).toPngBytes("Groceries", labels, LocalDateTime.of(2026, 10, 11, 12, 0))
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        assertEquals("image/png", options.outMimeType)
        return options.outWidth to options.outHeight
    }

    @Test
    fun `the image grows with the cart, not past it`() {
        val (shortWidth, shortHeight) = size(2)
        val (longWidth, longHeight) = size(60)
        assertEquals(shortWidth, longWidth)
        assertTrue("60 rows ($longHeight px) should be taller than 2 ($shortHeight px)", longHeight > shortHeight)
        // Longer than an A4 page: the image doesn't cut the receipt off.
        assertTrue(longHeight > longWidth * 842 / 595)
    }
}
