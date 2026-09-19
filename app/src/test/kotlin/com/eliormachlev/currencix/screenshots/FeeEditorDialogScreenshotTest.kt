package com.eliormachlev.currencix.screenshots

import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.view.preference.compose.FeeEditorDialog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

// Fee editor covers three shapes it renders in the app: a fresh global
// exchange fee (no delete button), an existing specific-pair fee (extra
// from/to/both-ways rows + delete button), and a specific-pair with a
// bothWays=true toggle so we lock down the switched-on state too.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class FeeEditorDialogScreenshotTest {
    @Test fun feeEditorNewGlobal() =
        captureMatrix("fee_editor_new_global") {
            FeeEditorDialog(
                titleRes = R.string.fee_section_global_exchange,
                existing = null,
                isPair = false,
                onDismiss = {},
                onConfirm = {},
                onPickCurrency = { _, _ -> },
                onDelete = null,
            )
        }

    @Test fun feeEditorExistingPair() =
        captureMatrix("fee_editor_existing_pair") {
            FeeEditorDialog(
                titleRes = R.string.fee_section_specific_pair,
                existing = SAMPLE_PAIR,
                isPair = true,
                onDismiss = {},
                onConfirm = {},
                onPickCurrency = { _, _ -> },
                onDelete = {},
            )
        }

    companion object {
        private val SAMPLE_PAIR =
            Fee.SpecificPair(
                id = "pair-1",
                name = "Wise USD→EUR",
                percent = BigDecimal("0.45"),
                from = "USD",
                to = "EUR",
                bothWays = true,
                isActive = true,
            )
    }
}
