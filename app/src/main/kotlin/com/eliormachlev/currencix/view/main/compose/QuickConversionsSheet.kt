package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import java.math.BigDecimal
import java.math.RoundingMode

private const val FEE_PERCENT_DECIMAL_PLACES = 2

/**
 * Compose-native quick conversions sheet — a [LedgerBottomSheet] wrapping the
 * shared [QuickConversionsContent] (currency-pair header + swap + rate rows).
 * Replaces the DialogFragment-based `QuickConversionsDialog` so callers no
 * longer need a `FragmentManager`. The content owns its own vertical scroll
 * so the sheet is created with `scrollableBody = false`.
 */
@Composable
fun QuickConversionsSheet(
    viewModel: MainViewModel,
    onSwap: () -> Unit,
    onOpenFees: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val costWithFeePrefix = stringResource(id = R.string.fee_cost_with_fee_prefix)
    val emptyText = stringResource(id = R.string.quick_conversions_no_rates)

    val from by viewModel.getBaseCurrency().observeAsState()
    val to by viewModel.getDestinationCurrency().observeAsState()
    val rates by viewModel.getExchangeRates().observeAsState()
    // Observed for recomposition; feeStackFor() itself isn't snapshot-observed.
    val fees by viewModel.getFees().collectAsStateWithLifecycle()

    val feeStack: BigDecimal =
        remember(from, to, fees) {
            if (from != null && to != null) viewModel.feeStackFor(from, to) else BigDecimal.ONE
        }
    val rows: List<QuickConversionsRow> =
        if (from != null && to != null && rates != null) {
            buildQuickConversionRows(
                QuickConversionRowInputs(
                    ctx = ctx,
                    from = from!!,
                    to = to!!,
                    rates = rates!!,
                    feeStack = feeStack,
                    costWithFeePrefix = costWithFeePrefix,
                ),
            )
        } else {
            emptyList()
        }
    val feeInfoText = quickConversionsFeeInfoText(ctx, feeStack)

    LedgerBottomSheet(
        title = stringResource(id = R.string.quick_conversions_title),
        onDismiss = onDismiss,
        scrollableBody = false,
    ) {
        QuickConversionsContent(
            from = from,
            to = to,
            feeInfoText = feeInfoText,
            rows = rows,
            emptyText = emptyText,
            onSwap = onSwap,
            onSwapLongPress = onOpenFees,
        )
    }
}

// Top-of-sheet "fees applied" line — omitted when the stack is neutral.
private fun quickConversionsFeeInfoText(
    ctx: Context,
    stack: BigDecimal,
): String? {
    if (stack.isNeutralFeeStack()) return null
    val percent = stack.feePercentDelta(FEE_PERCENT_DECIMAL_PLACES, RoundingMode.HALF_UP)
    val sign = if (percent.signum() >= 0) "+" else ""
    return ctx.getString(R.string.quick_conversions_fees_applied, "$sign$percent%")
}
