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
                        Currency.ILS,
                        Currency.USD,
                        FEE_ROWS,
                        feeInfoText = "Fees applied: +1%",
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
                QuickConversionsRow(
                    "ILS ₪ 1",
                    "USD $ 0.32",
                    "+ 1% = USD $ 0.33",
                    fromValue = "1",
                    toValue = "0.32",
                    withFeeValue = "0.33",
                    feePercent = "+ 1%",
                ),
                QuickConversionsRow(
                    "ILS ₪ 37.5",
                    "USD $ 12.18",
                    "+ 1% = USD $ 12.30",
                    isTyped = true,
                    fromValue = "37.5",
                    toValue = "12.18",
                    withFeeValue = "12.30",
                    feePercent = "+ 1%",
                ),
                QuickConversionsRow(
                    "ILS ₪ 100",
                    "USD $ 32.49",
                    "+ 1% = USD $ 32.81",
                    fromValue = "100",
                    toValue = "32.49",
                    withFeeValue = "32.81",
                    feePercent = "+ 1%",
                ),
            )

        private val SAMPLE_ROWS =
            listOf(
                QuickConversionsRow("USD $ 1", "EUR € 0.92", null, fromValue = "1", toValue = "0.92"),
                QuickConversionsRow("USD $ 5", "EUR € 4.60", null, fromValue = "5", toValue = "4.60"),
                QuickConversionsRow("USD $ 10", "EUR € 9.20", null, fromValue = "10", toValue = "9.20"),
                QuickConversionsRow("USD $ 50", "EUR € 46.00", null, fromValue = "50", toValue = "46.00"),
                QuickConversionsRow("USD $ 100", "EUR € 92.00", null, fromValue = "100", toValue = "92.00"),
            )
    }
}
