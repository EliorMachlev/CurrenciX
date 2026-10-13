package com.eliormachlev.currencix.screenshots

import android.app.Application
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.view.main.compose.QuickConversionsActions
import com.eliormachlev.currencix.view.main.compose.QuickConversionsContent
import com.eliormachlev.currencix.view.main.compose.QuickConversionsRow
import com.eliormachlev.currencix.view.main.compose.QuickConversionsTable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class QuickConversionsScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    @Test fun quickConversionsPopulated() =
        shots.captureMatrix("quick_conversions_populated") {
            QuickConversionsContent(
                table =
                    QuickConversionsTable(
                        Currency.USD,
                        Currency.EUR,
                        SAMPLE_ROWS,
                        sourceText = "Based on Frankfurter.app, 12/10/26 16:00",
                    ),
                emptyText = "No conversions",
                actions = ACTIONS,
            )
        }

    // A fee in force, the typed amount among the rows, and why the rates may not be current.
    @Test fun quickConversionsWithFeeAndTypedAmount() =
        shots.captureMatrix("quick_conversions_fee_typed") {
            QuickConversionsContent(
                table =
                    QuickConversionsTable(
                        Currency.USD,
                        Currency.EUR,
                        FEE_ROWS,
                        feeInfoText = "Fees applied: +2.5%",
                        sourceText = "Based on Bank of Israel, 12/10/26",
                        warningText = "Offline • last updated 12/10/26",
                    ),
                emptyText = "No conversions",
                actions = ACTIONS,
            )
        }

    @Test fun quickConversionsEmpty() =
        shots.captureMatrix("quick_conversions_empty") {
            QuickConversionsContent(
                table = QuickConversionsTable(Currency.USD, Currency.EUR, emptyList()),
                emptyText = "No conversions available",
                actions = ACTIONS,
            )
        }

    companion object {
        private val ACTIONS = QuickConversionsActions(onSwap = {}, onSwapLongPress = {}, onShare = {})

        private val FEE_ROWS =
            listOf(
                QuickConversionsRow("10 USD", "9.20 EUR", "Cost with fee: \u2066 9.43 EUR\u2069"),
                QuickConversionsRow("37.5 USD", "34.50 EUR", "Cost with fee: \u2066 35.36 EUR\u2069", isTyped = true),
                QuickConversionsRow("50 USD", "46.00 EUR", "Cost with fee: \u2066 47.15 EUR\u2069"),
            )

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
