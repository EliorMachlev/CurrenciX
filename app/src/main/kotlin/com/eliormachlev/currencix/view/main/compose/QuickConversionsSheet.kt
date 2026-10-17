package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.shareImageOrText
import com.eliormachlev.currencix.view.compose.LayerCapture
import com.eliormachlev.currencix.view.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.rememberDatabase
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.main.ConverterStatus
import com.eliormachlev.currencix.view.main.copyToClipboard
import com.eliormachlev.currencix.view.navigation.PillSide
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

private const val FEE_PERCENT_DECIMAL_PLACES = 2

// The shared image's file name starts with this.
private const val SHARE_NAME_PREFIX = "currencix-quick"

/**
 * Quick conversions: the pair at a spread of round amounts, plus the amount
 * typed in the converter, in a [LedgerBottomSheet] over
 * [QuickConversionsContent]. A row's tap puts its amount into the converter
 * and closes the sheet; a long-press copies it. The recent pairs sit on top
 * to switch without closing, the rates' source and any reason they may not
 * be current sit under the rows, and Share sends the table as an image with
 * a text version. The content owns its own vertical scroll, so the sheet is
 * created with `scrollableBody = false`.
 */
@Composable
internal fun QuickConversionsSheet(
    viewModel: MainViewModel,
    status: ConverterStatus,
    onSwap: () -> Unit,
    onOpenFees: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val table = rememberQuickConversionsTable(viewModel, status)
    val capture = remember { LayerCapture() }
    val scope = rememberCoroutineScope()
    // A pill's picker, over the sheet: the converter's own.
    var pickerSide by remember { mutableStateOf<PillSide?>(null) }
    val actions =
        remember(viewModel, table, onSwap, onOpenFees, onDismiss) {
            QuickConversionsActions(
                onSwap = onSwap,
                onSwapLongPress = onOpenFees,
                onFromClick = { pickerSide = PillSide.FROM },
                onToClick = { pickerSide = PillSide.TO },
                onUseAmount = { amount ->
                    viewModel.input.setAmount(amount)
                    onDismiss()
                },
                onCopy = { row -> copyToClipboard(ctx, row.copyText) },
                onShare = {
                    scope.launch { shareImageOrText(ctx, capture.capture(), namePrefix = SHARE_NAME_PREFIX, text = table.shareText()) }
                },
            )
        }

    LedgerBottomSheet(
        title = stringResource(id = R.string.quick_conversions_title),
        onDismiss = onDismiss,
        scrollableBody = false,
    ) {
        QuickConversionsContent(
            table = table,
            emptyText = stringResource(id = R.string.quick_conversions_no_rates),
            actions = actions,
            top = { QuickConversionsRecentPairs(viewModel, table.from, table.to) },
            capture = capture,
        )
    }
    pickerSide?.let { side -> ConverterCurrencyPicker(side = side, viewModel = viewModel, onDismiss = { pickerSide = null }) }
}

// The table for the pair on screen, the typed amount among its rows, and
// where its rates come from.
@Composable
private fun rememberQuickConversionsTable(
    viewModel: MainViewModel,
    status: ConverterStatus,
): QuickConversionsTable {
    val ctx = LocalContext.current
    val from by viewModel.getBaseCurrency().observeAsState()
    val to by viewModel.getDestinationCurrency().observeAsState()
    val rates by viewModel.getExchangeRates().observeAsState()
    val typed by viewModel.getCurrentBaseValueAsNumber().observeAsState()
    // Observed for recomposition; feeStackFor() itself isn't snapshot-observed.
    val fees by viewModel.getFees().collectAsStateWithLifecycle()
    val decimalPlaces by viewModel.getDecimalPlaces().collectAsStateWithLifecycle()
    val warning = status.banner.value?.text

    val feeStack: BigDecimal =
        remember(from, to, fees) {
            val pairFrom = from
            val pairTo = to
            if (pairFrom != null && pairTo != null) viewModel.feeStackFor(pairFrom, pairTo) else BigDecimal.ONE
        }
    return remember(from, to, rates, typed, feeStack, decimalPlaces, warning) {
        val pairFrom = from
        val pairTo = to
        val current = rates
        val rows =
            if (pairFrom != null && pairTo != null && current != null) {
                buildQuickConversionRows(QuickConversionRowInputs(ctx, pairFrom, pairTo, current, feeStack, typed, decimalPlaces))
            } else {
                emptyList()
            }
        QuickConversionsTable(
            from = pairFrom,
            to = pairTo,
            rows = rows,
            feeInfoText = quickConversionsFeeInfoText(ctx, feeStack),
            sourceText = current?.let { ratesSource(ctx, it, status) },
            warningText = warning,
        )
    }
}

// "Based on Frankfurter.app, 12/10/26 16:00"; null before rates arrive.
private fun ratesSource(
    ctx: Context,
    rates: ExchangeRates,
    status: ConverterStatus,
): String? {
    val provider = rates.provider?.getName(ctx) ?: return null
    val timestamp = status.formatTimestamp(rates.date, rates.time) ?: return null
    return ctx.getString(R.string.share_footer, provider, timestamp)
}

// The shared text: one "ILS ₪ 1 = USD $ 0.3249" line per row (its cost with
// the fee after it), then where the rates come from, as the converter's share ends.
private fun QuickConversionsTable.shareText(): String =
    buildString {
        rows.forEach { row -> appendLine(row.copyText) }
        sourceText?.let { append("\n-- ").append(it) }
    }.trimEnd()

// The recent pairs other than the one on screen: a tap switches the table.
@Composable
private fun QuickConversionsRecentPairs(
    viewModel: MainViewModel,
    from: Currency?,
    to: Currency?,
) {
    val database = rememberDatabase()
    val recents by database.lastState.recentPairs.collectAsStateWithLifecycle()
    val current = if (from != null && to != null && from != to) CurrencyPair(from, to) else null
    val others = remember(recents, current) { recents.filterNot { it.isSameCurrencies(current) }.toImmutableList() }
    if (others.isEmpty()) return
    RecentPairsRow(
        pairs = others,
        onPick = viewModel::setCurrencyPair,
        onRemove = { database.lastState.removeRecentPair(it) },
        contentPadding = PaddingValues(horizontal = Spacing.Large),
        modifier = Modifier.padding(top = Spacing.Small),
    )
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
