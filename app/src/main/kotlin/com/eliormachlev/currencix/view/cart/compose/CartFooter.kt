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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.feeStackDelta
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.toCartFeePercentDisplay
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
 * Cart footer — currency-pair header (chip / swap / chip), subtotal,
 * fee-annotation rows, then total. Ports the layout previously assembled
 * from `activity_cart.xml` + [CartFooterBinding], keeping the same
 * two-row fee equation (delta + total-with-fee) and the LTR-only
 * currency row so "from" stays left of "to" in every locale.
 */
@Composable
fun CartFooter(
    viewModel: CartViewModel,
    fragmentManager: FragmentManager,
    onOpenFees: () -> Unit,
) {
    val baseCurrency by viewModel.getBaseCurrency().observeAsState()
    val destCurrency by viewModel.getDestinationCurrency().observeAsState()
    val subtotal by viewModel.getSubtotal().observeAsState()
    val total by viewModel.getTotal().observeAsState()
    // Fees and rates aren't rendered directly, but currentFeeStack() reads
    // from both — observing them here keeps the fee-annotation rows in sync
    // when either source emits.
    val fees by viewModel.getFees().observeAsState()
    val rates by viewModel.getExchangeRates().observeAsState()
    val feeStack = remember(fees, rates, baseCurrency, destCurrency) { viewModel.currentFeeStack() }
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
            fragmentManager = fragmentManager,
            baseCurrency = baseCurrency,
            destCurrency = destCurrency,
            onBasePicked = viewModel::setBaseCurrency,
            onDestPicked = viewModel::setDestinationCurrency,
            onSwapClick = viewModel::swapCurrencies,
            onSwapLongPress = onOpenFees,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = ROW_TOP_GAP),
        ) {
            Text(
                text = stringResource(id = R.string.cart_subtotal_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = context.formatCartAmount(subtotal, baseCurrency),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        FeeAnnotationRow(
            prefixRes = R.string.fee_true_cost_prefix,
            feeStack = feeStack,
            base = subtotal,
            currency = baseCurrency,
            mode = FeeRowMode.DELTA,
        )
        FeeAnnotationRow(
            prefixRes = R.string.fee_cost_with_fee_prefix,
            feeStack = feeStack,
            base = subtotal,
            currency = baseCurrency,
            mode = FeeRowMode.TOTAL,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = TOTAL_TOP_GAP),
        ) {
            Text(
                text = stringResource(id = R.string.cart_total_label),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = context.formatCartAmount(total, destCurrency),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

// Fee annotation row: hidden entirely when the stack is neutral. TOTAL renders
// `base * stack`, DELTA renders `|base * (stack - 1)|` with the percent tail.
// Mirrors renderFeeExtraRow in the old CartFooterBinding.
@Composable
private fun FeeAnnotationRow(
    prefixRes: Int,
    feeStack: BigDecimal,
    base: BigDecimal?,
    currency: Currency?,
    mode: FeeRowMode,
) {
    if (feeStack.isNeutralFeeStack()) return
    val context = LocalContext.current
    val multiplier =
        when (mode) {
            FeeRowMode.TOTAL -> feeStack
            FeeRowMode.DELTA -> feeStack.feeStackDelta()
        }
    val raw = (base ?: BigDecimal.ZERO).multiply(multiplier, MathContext.DECIMAL128)
    val adjusted = if (mode == FeeRowMode.DELTA) raw.abs() else raw
    val amountText = context.formatCartAmount(adjusted, currency)
    val valueText =
        when (mode) {
            FeeRowMode.TOTAL -> amountText
            FeeRowMode.DELTA ->
                context.getString(
                    R.string.cart_fee_extra_value,
                    amountText,
                    feeStack.toCartFeePercentDisplay(),
                )
        }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stripLabelSeparator(context.getString(prefixRes)),
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
    fragmentManager: FragmentManager,
    baseCurrency: Currency?,
    destCurrency: Currency?,
    onBasePicked: (Currency) -> Unit,
    onDestPicked: (Currency) -> Unit,
    onSwapClick: () -> Unit,
    onSwapLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CURRENCY_ROW_GAP),
    ) {
        CartCurrencyChip(
            fragmentManager = fragmentManager,
            currency = baseCurrency,
            disabledCurrency = destCurrency,
            onCurrencyPicked = onBasePicked,
            modifier = Modifier.weight(1f),
        )
        SwapFab(onClick = onSwapClick, onLongClick = onSwapLongPress)
        CartCurrencyChip(
            fragmentManager = fragmentManager,
            currency = destCurrency,
            disabledCurrency = baseCurrency,
            onCurrencyPicked = onDestPicked,
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

private enum class FeeRowMode { TOTAL, DELTA }

// Existing prefix strings end with a locale-specific ": " / " : " / "：" for
// inline use. Trim it here so the label sits flush left of the right column.
private fun stripLabelSeparator(text: String): String = text.trimEnd(' ', '\u00A0', ':', '：')
