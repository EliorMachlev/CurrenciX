package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.rateFor
import com.eliormachlev.currencix.util.feeStackDelta
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.toCartFeePercentDisplay
import com.eliormachlev.currencix.view.compose.CurrencyPairRow
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.PairRowActions
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerSheet
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel
import com.eliormachlev.currencix.viewmodel.cart.budgetLeft
import com.eliormachlev.currencix.viewmodel.cart.perPerson
import java.math.BigDecimal
import java.math.MathContext

private val FOOTER_OUTER_MARGIN: Dp = 16.dp
private val FOOTER_PADDING: Dp = 16.dp
private val FOOTER_RADIUS: Dp = 16.dp
private val ROW_TOP_GAP: Dp = 16.dp
private val TOTAL_TOP_GAP: Dp = 8.dp
private val SWAP_FAB_SIZE: Dp = 44.dp
private val SWAP_ICON_SIZE: Dp = 22.dp
private val EXTRA_ROW_GAP: Dp = 4.dp

/** The numbers the footer shows. */
@Immutable
data class CartTotals(
    val baseCurrency: Currency?,
    val destCurrency: Currency?,
    val subtotal: BigDecimal?,
    val convertedSubtotal: BigDecimal?,
    val total: BigDecimal?,
    // Fees and rates multiplied out; 1 when no fee applies.
    val feeStack: BigDecimal,
    val extras: CartExtras = CartExtras(),
    val tip: BigDecimal? = null,
)

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
    onEditExtras: () -> Unit,
) {
    val rates by viewModel.getExchangeRates().observeAsState()
    val totals = observeTotals(viewModel, rates)
    var pickerSide by remember { mutableStateOf<CartPickSide?>(null) }
    val pairActions =
        remember(viewModel, onOpenFees) {
            PairRowActions(
                onFromClick = { pickerSide = CartPickSide.FROM },
                onToClick = { pickerSide = CartPickSide.TO },
                onSwap = viewModel::swapCurrencies,
                onSwapLongPress = onOpenFees,
            )
        }
    CartFooterCard(totals = totals, pair = pairActions, onEditExtras = onEditExtras)
    pickerSide?.let { side ->
        CartCurrencyPickerHost(
            side = side,
            totals = totals,
            rates = rates,
            onPicked = if (side == CartPickSide.FROM) viewModel::setBaseCurrency else viewModel::setDestinationCurrency,
            onDismiss = { pickerSide = null },
        )
    }
}

@Composable
private fun observeTotals(
    viewModel: CartViewModel,
    rates: ExchangeRates?,
): CartTotals {
    val baseCurrency by viewModel.getBaseCurrency().observeAsState()
    val destCurrency by viewModel.getDestinationCurrency().observeAsState()
    val subtotal by viewModel.getSubtotal().observeAsState()
    val convertedSubtotal by viewModel.getConvertedSubtotal().observeAsState()
    val total by viewModel.getTotal().observeAsState()
    val extras by viewModel.getExtras().observeAsState()
    val tip by viewModel.getTip().observeAsState()
    // Fees and rates aren't rendered directly, but currentFeeStack() reads
    // from both — observing them here keeps the fee-annotation rows in sync
    // when either source emits.
    val fees by viewModel.getFees().observeAsState()
    val feeStack = remember(fees, rates, baseCurrency, destCurrency) { viewModel.currentFeeStack() }
    return CartTotals(baseCurrency, destCurrency, subtotal, convertedSubtotal, total, feeStack, extras ?: CartExtras(), tip)
}

/**
 * Static presentation of the footer — currency row + subtotal + fee delta +
 * total — apart from [CartFooter], which holds the observed state and the
 * picker sheet.
 */
@Composable
internal fun CartFooterCard(
    totals: CartTotals,
    pair: PairRowActions,
    onEditExtras: () -> Unit,
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
        // From on the left, to on the right, as on the converter, in every language.
        Ltr {
            CurrencyPairRow(from = totals.baseCurrency, to = totals.destCurrency, actions = pair) {
                SwapFab(onClick = pair.onSwap, onLongClick = pair.onSwapLongPress)
            }
        }
        AmountRow(
            topGap = ROW_TOP_GAP,
            labelRes = R.string.cart_subtotal_label,
            amount = context.formatCartAmount(totals.subtotal, totals.baseCurrency),
            style = MaterialTheme.typography.titleSmall,
        )
        totals.extras.tipPercent?.let { percent ->
            ExtraRow(
                label = stringResource(R.string.cart_tip_row, percent.toPlainString()),
                amount = "+" + context.formatCartAmount(totals.tip, totals.baseCurrency),
                onClick = onEditExtras,
            )
        }
        FeeAnnotationRow(
            prefixRes = R.string.fee_true_cost_prefix,
            feeStack = totals.feeStack,
            base = totals.convertedSubtotal,
            currency = totals.destCurrency,
        )
        AmountRow(
            topGap = TOTAL_TOP_GAP,
            labelRes = R.string.cart_total_label,
            amount = context.formatCartAmount(totals.total, totals.destCurrency),
            style = MaterialTheme.typography.titleLarge,
        )
        ExtrasBelowTotal(totals.extras, totals.total ?: BigDecimal.ZERO, totals.destCurrency, onEditExtras)
    }
}

// "Per person (÷3)" and "Left in budget" / "Over budget" under the total,
// each only when set; tapping one edits them.
@Composable
private fun ExtrasBelowTotal(
    extras: CartExtras,
    total: BigDecimal,
    currency: Currency?,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    if (extras.splitWays > 1) {
        ExtraRow(
            label = stringResource(R.string.cart_per_person_row, extras.splitWays),
            amount = context.formatCartAmount(perPerson(total, extras.splitWays), currency),
            onClick = onEdit,
        )
    }
    extras.budget?.let { budget ->
        val left = budgetLeft(total, budget)
        val over = left.signum() < 0
        ExtraRow(
            label = stringResource(if (over) R.string.cart_over_budget_row else R.string.cart_budget_left_row),
            amount = context.formatCartAmount(left.abs(), currency),
            onClick = onEdit,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// A small label + amount line for the cart's extras (tip, split, budget).
@Composable
private fun ExtraRow(
    label: String,
    amount: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(top = EXTRA_ROW_GAP),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.weight(1f))
        Figure(amount, MaterialTheme.typography.labelMedium, color)
    }
}

private enum class CartPickSide { FROM, TO }

/**
 * The picker sheet for the side the user tapped: it prices each currency
 * against the other side's amount, and greys that side's currency out.
 */
@Composable
private fun CartCurrencyPickerHost(
    side: CartPickSide,
    totals: CartTotals,
    rates: ExchangeRates?,
    onPicked: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    val picksFrom = side == CartPickSide.FROM
    val other = if (picksFrom) totals.destCurrency else totals.baseCurrency
    val otherSum = if (picksFrom) totals.convertedSubtotal else totals.subtotal
    CurrencyPickerSheet(
        currentRate = other?.let { c -> rates?.rateFor(c)?.let { Rate(c, it.value) } },
        currentSum = otherSum ?: BigDecimal.ONE,
        disabledCurrency = other,
        onRateClicked = { rate -> onPicked(rate.currency) },
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
        Figure(amount, style)
    }
}

// An amount ("0.00 $", "+1.20 ₪ (3.00%)"): reads left to right in every
// language, at the row's end edge either way.
@Composable
private fun Figure(
    text: String,
    style: TextStyle,
    color: Color = Color.Unspecified,
) {
    Ltr { Text(text = text, style = style, color = color) }
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
        Figure(valueText, MaterialTheme.typography.labelSmall, MaterialTheme.colorScheme.error)
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
            painter = painterResource(R.drawable.ic_swap_horiz),
            contentDescription = stringResource(id = R.string.desc_toggle_currencies),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(SWAP_ICON_SIZE),
        )
    }
}

// Existing prefix strings end with a locale-specific ": " / " : " / "：" for
// inline use. Trim it here so the label sits flush left of the right column.
private fun stripLabelSeparator(text: String): String = text.trimEnd(' ', '\u00A0', ':', '：')
