package com.eliormachlev.currencix.screenshots

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.view.main.compose.QuickConversionsContent
import com.eliormachlev.currencix.view.main.compose.QuickConversionsRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class QuickConversionsScreenshotTest {
    @Test fun quickConversionsPopulated() =
        captureMatrix("quick_conversions_populated") {
            QuickConversionsContent(
                from = Currency.USD,
                to = Currency.EUR,
                feeInfoText = null,
                rows = SAMPLE_ROWS,
                emptyText = "No conversions",
                onSwap = {},
                onSwapLongPress = {},
            )
        }

    @Test fun quickConversionsEmpty() =
        captureMatrix("quick_conversions_empty") {
            QuickConversionsContent(
                from = Currency.USD,
                to = Currency.EUR,
                feeInfoText = null,
                rows = emptyList(),
                emptyText = "No conversions available",
                onSwap = {},
                onSwapLongPress = {},
            )
        }

    companion object {
        private val SAMPLE_ROWS =
            listOf(
                QuickConversionsRow("$1", "€0.92", null),
                QuickConversionsRow("$5", "€4.60", null),
                QuickConversionsRow("$10", "€9.20", null),
                QuickConversionsRow("$50", "€46.00", null),
                QuickConversionsRow("$100", "€92.00", null),
            )
    }
}
