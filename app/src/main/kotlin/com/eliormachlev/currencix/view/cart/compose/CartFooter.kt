package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.rateFor
import com.eliormachlev.currencix.util.feeStackDelta
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.toCartFeePercentDisplay
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerSheet
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel
import java.math.BigDecimal
import java.math.MathContext

private val FOOTER_OUTER_MARGIN: Dp = 16.dp
private val FOOTER_PADDING: Dp = 16.dp
private val FOOTER_RADIUS: Dp = 16.dp
private val ROW_TOP_GAP: Dp = 16.dp
private val TOTAL_TOP_GAP: Dp = 8.dp
private val CURRENCY_ROW_GAP: Dp = 8.dp
private val SWAP_FAB_SIZE: Dp = 44.dp
private val SWAP_ICON_SIZE: Dp = 22.dp

/**
 * Cart footer — currency-pair header (chip / swap / chip), subtotal in
 * the base currency, a fee-delta annotation (destination side, hidden
 * when neutral), then the fee-inflated total in the destination
 * currency. LTR-only currency row keeps "from" left of "to" in every
 * locale.
 */
@Composable
fun CartFooter(
    viewModel: CartViewModel,
    onOpenFees: () -> Unit,
) {
    val baseCurrency by viewModel.getBaseCurrency().observeAsState()
    val destCurrency by viewModel.getDestinationCurrency().observeAsState()
    val subtotal by viewModel.getSubtotal().observeAsState()
    val convertedSubtotal by viewModel.getConvertedSubtotal().observeAsState()
    val total by viewModel.getTotal().observeAsState()
    // Fees and rates aren't rendered directly, but currentFeeStack() reads
    // from both — observing them here keeps the fee-annotation rows in sync
    // when either source emits.
    val fees by viewModel.getFees().observeAsState()
    val rates by viewModel.getExchangeRates().observeAsState()
    val feeStack = remember(fees, rates, baseCurrency, destCurrency) { viewModel.currentFeeStack() }

    var pickerSide by remember { mutableStateOf<CartPickSide?>(null) }
    CartFooterCard(
        baseCurrency = baseCurrency,
        destCurrency = destCurrency,
        subtotal = subtotal,
        convertedSubtotal = convertedSubtotal,
        total = total,
        feeStack = feeStack,
        onOpenFees = onOpenFees,
        onBaseClick = { pickerSide = CartPickSide.FROM },
        onDestClick = { pickerSide = CartPickSide.TO },
        onSwapClick = viewModel::swapCurrencies,
    )
    pickerSide?.let { side ->
        CartCurrencyPickerHost(
            side = side,
            baseCurrency = baseCurrency,
            destCurrency = destCurrency,
            subtotal = subtotal,
            convertedSubtotal = convertedSubtotal,
            rates = rates,
            onBasePicked = viewModel::setBaseCurrency,
            onDestPicked = viewModel::setDestinationCurrency,
            onDismiss = { pickerSide = null },
        )
    }
}

/**
 * Static presentation of the footer — currency row + subtotal + fee delta +
 * total. Extracted from [CartFooter] so the parent can stay under the
 * LongMethod threshold while the picker sheet + observed state stay hoisted
 * where they belong (with the ViewModel).
 */
@Composable
@Suppress("LongParameterList")
private fun CartFooterCard(
    baseCurrency: Currency?,
    destCurrency: Currency?,
    subtotal: BigDecimal?,
    convertedSubtotal: BigDecimal?,
    total: BigDecimal?,
    feeStack: BigDecimal,
    onOpenFees: () -> Unit,
    onBaseClick: () -> Unit,
    onDestClick: () -> Unit,
    onSwapClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(FOOTER_OUTER_MARGIN)
                .clip(RoundedCornerShape(FOOTER_RADIUS))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(FOOTER_PADDING),
    ) {
        CurrencyRow(
            baseCurrency = baseCurrency,
            destCurrency = destCurrency,
            onBaseClick = onBaseClick,
            onDestClick = onDestClick,
            onSwapClick = onSwapClick,
            onSwapLongPress = onOpenFees,
        )
        AmountRow(
            topGap = ROW_TOP_GAP,
            labelRes = R.string.cart_subtotal_label,
            amount = context.formatCartAmount(subtotal, baseCurrency),
            style = MaterialTheme.typography.titleSmall,
        )
        FeeAnnotationRow(
            prefixRes = R.string.fee_true_cost_prefix,
            feeStack = feeStack,
            base = convertedSubtotal,
            currency = destCurrency,
        )
        AmountRow(
            topGap = TOTAL_TOP_GAP,
            labelRes = R.string.cart_total_label,
            amount = context.formatCartAmount(total, destCurrency),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

private enum class CartPickSide { FROM, TO }

/**
 * Resolves the reference rate / sum / disabled currency for the picker sheet
 * based on which side ([side]) the user tapped. Extracted so [CartFooter]
 * itself stays under the LongMethod threshold and the compute-then-render
 * block reads on its own — mirrors `CurrencyPickerHost` on the main hero.
 */
@Composable
@Suppress("LongParameterList")
private fun CartCurrencyPickerHost(
    side: CartPickSide,
    baseCurrency: Currency?,
    destCurrency: Currency?,
    subtotal: BigDecimal?,
    convertedSubtotal: BigDecimal?,
    rates: ExchangeRates?,
    onBasePicked: (Currency) -> Unit,
    onDestPicked: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    val disabled = if (side == CartPickSide.FROM) destCurrency else baseCurrency
    val refCurrency = if (side == CartPickSide.FROM) destCurrency else baseCurrency
    val refRate =
        refCurrency?.let { c -> rates?.rateFor(c)?.let { Rate(c, it.value) } }
    val refSum = (if (side == CartPickSide.FROM) convertedSubtotal else subtotal) ?: BigDecimal.ONE
    CurrencyPickerSheet(
        currentRate = refRate,
        currentSum = refSum,
        disabledCurrency = disabled,
        onRateClicked = { rate ->
            if (side == CartPickSide.FROM) onBasePicked(rate.currency) else onDestPicked(rate.currency)
        },
        onDismiss = onDismiss,
    )
}

// Label + right-aligned amount, used for both the subtotal and total rows
// so the two share label-weight, gap, and column layout without a copy-paste.
@Composable
private fun AmountRow(
    topGap: Dp,
    labelRes: Int,
    amount: String,
    style: TextStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = topGap),
    ) {
        Text(
            text = stringResource(id = labelRes),
            style = style,
            modifier = Modifier.weight(1f),
        )
        Text(text = amount, style = style)
    }
}

// Delta row between the fee-free converted subtotal and the fee-inflated
// Total. Hidden when the stack is neutral. Base + currency are on the
// destination side because real-world FX fees are charged on the converted
// amount, not the source subtotal.
@Composable
private fun FeeAnnotationRow(
    prefixRes: Int,
    feeStack: BigDecimal,
    base: BigDecimal?,
    currency: Currency?,
) {
    if (feeStack.isNeutralFeeStack()) return
    val context = LocalContext.current
    val delta = (base ?: BigDecimal.ZERO).multiply(feeStack.feeStackDelta(), MathContext.DECIMAL128).abs()
    val amountText = context.formatCartAmount(delta, currency)
    val valueText =
        stringResource(
            id = R.string.cart_fee_extra_value,
            amountText,
            feeStack.toCartFeePercentDisplay(),
        )
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stripLabelSeparator(stringResource(id = prefixRes)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CurrencyRow(
    baseCurrency: Currency?,
    destCurrency: Currency?,
    onBaseClick: () -> Unit,
    onDestClick: () -> Unit,
    onSwapClick: () -> Unit,
    onSwapLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CURRENCY_ROW_GAP),
    ) {
        CartCurrencyChip(
            currency = baseCurrency,
            onClick = onBaseClick,
            modifier = Modifier.weight(1f),
        )
        SwapFab(onClick = onSwapClick, onLongClick = onSwapLongPress)
        CartCurrencyChip(
            currency = destCurrency,
            onClick = onDestClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SwapFab(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .size(SWAP_FAB_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = stringResource(id = R.string.desc_toggle_currencies),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(SWAP_ICON_SIZE),
        )
    }
}

// Existing prefix strings end with a locale-specific ": " / " : " / "：" for
// inline use. Trim it here so the label sits flush left of the right column.
private fun stripLabelSeparator(text: String): String = text.trimEnd(' ', '\u00A0', ':', '：')
