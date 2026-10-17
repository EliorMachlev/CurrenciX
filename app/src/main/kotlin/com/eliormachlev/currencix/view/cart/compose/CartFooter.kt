package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Person
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.rateFor
import com.eliormachlev.currencix.util.HUNDRED_PERCENT
import com.eliormachlev.currencix.util.feeStackDelta
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.util.toCartFeePercentDisplay
import com.eliormachlev.currencix.view.compose.CurrencyPairRow
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.PairRowActions
import com.eliormachlev.currencix.view.compose.countTo
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.main.DEFAULT_DATE_PATTERN
import com.eliormachlev.currencix.view.main.formatRatesTimestamp
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerSheet
import com.eliormachlev.currencix.view.navigation.PillSide
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel
import com.eliormachlev.currencix.viewmodel.cart.budgetLeft
import com.eliormachlev.currencix.viewmodel.cart.perPerson
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val FOOTER_OUTER_MARGIN: Dp = Spacing.Large
private val FOOTER_PADDING: Dp = Spacing.Large
private val FOOTER_RADIUS: Dp = 16.dp
private val ROW_TOP_GAP: Dp = Spacing.Large
private val TOTAL_TOP_GAP: Dp = Spacing.Small
private val EXTRA_ROW_GAP: Dp = Spacing.Tiny
private val COLLAPSE_BUTTON_SIZE: Dp = 32.dp
private val COLLAPSE_ICON_SIZE: Dp = 20.dp

// Providers publish on working days, so a long weekend leaves rates a few
// days old as a matter of course; older than this, the card says how old.
internal const val RATES_OLD_AFTER_DAYS = 4L

/** Whether rates dated [date] are older than a long weekend explains, on [today]. */
internal fun ratesAreOld(
    date: LocalDate?,
    today: LocalDate = LocalDate.now(),
): Boolean = date != null && ChronoUnit.DAYS.between(date, today) > RATES_OLD_AFTER_DAYS

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
    // What the ticked items come to (shopping-list mode); null otherwise.
    val basket: BigDecimal? = null,
    // The rates' date, formatted, when they're old (see [ratesAreOld]); null otherwise.
    val oldRatesDate: String? = null,
    // What each person pays, when the cart splits by person; empty otherwise.
    val shares: List<Pair<Person, BigDecimal>> = emptyList(),
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
    val collapsed by viewModel.isTotalsCollapsed.observeAsState(initial = false)
    var pickerSide by remember { mutableStateOf<PillSide?>(null) }
    val pairActions =
        remember(viewModel, onOpenFees) {
            PairRowActions(
                onFromClick = { pickerSide = PillSide.FROM },
                onToClick = { pickerSide = PillSide.TO },
                onSwap = viewModel::swapCurrencies,
                onSwapLongPress = onOpenFees,
            )
        }
    CartFooterCard(
        totals = totals,
        pair = pairActions,
        onEditExtras = onEditExtras,
        collapsed = collapsed,
        onToggleCollapsed = { viewModel.setTotalsCollapsed(!collapsed) },
    )
    pickerSide?.let { side ->
        CartCurrencyPickerHost(
            side = side,
            totals = totals,
            rates = rates,
            onPicked = if (side == PillSide.FROM) viewModel::setBaseCurrency else viewModel::setDestinationCurrency,
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
    val shoppingMode by viewModel.isShoppingMode().observeAsState(initial = false)
    val basket by viewModel.getBasketTotal().observeAsState()
    val shares by viewModel.getPersonShares().observeAsState(emptyList())
    val datePattern by viewModel.dateFormat.observeAsState(initial = DEFAULT_DATE_PATTERN)
    val oldRatesDate =
        remember(rates, datePattern) {
            rates?.takeIf { ratesAreOld(it.date) }?.let { formatRatesTimestamp(datePattern, it.date, it.time) }
        }
    return CartTotals(
        baseCurrency = baseCurrency,
        destCurrency = destCurrency,
        subtotal = subtotal,
        convertedSubtotal = convertedSubtotal,
        total = total,
        feeStack = feeStack,
        extras = extras ?: CartExtras(),
        tip = tip,
        basket = basket.takeIf { shoppingMode },
        oldRatesDate = oldRatesDate,
        shares = shares,
    )
}

/**
 * Static presentation of the footer — currency row + subtotal + fee delta +
 * total — apart from [CartFooter], which holds the observed state and the
 * picker sheet. [collapsed] leaves the currency row and the total (and what's
 * in the basket); the chevron by the total ([onToggleCollapsed]) switches.
 */
@Composable
internal fun CartFooterCard(
    totals: CartTotals,
    pair: PairRowActions,
    onEditExtras: () -> Unit,
    collapsed: Boolean = false,
    onToggleCollapsed: () -> Unit = {},
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
            CurrencyPairRow(from = totals.baseCurrency, to = totals.destCurrency, actions = pair)
        }
        AnimatedVisibility(visible = !collapsed) { AboveTotal(totals, onEditExtras) }
        // Counts to a new total (an item added, a price changed), not to another currency's.
        val shownTotal = countTo(totals.total, key = totals.destCurrency)
        AmountRow(
            topGap = if (collapsed) ROW_TOP_GAP else TOTAL_TOP_GAP,
            labelRes = R.string.cart_total_label,
            amount = context.formatCartAmount(shownTotal, totals.destCurrency),
            style = MaterialTheme.typography.titleLarge,
            afterLabel = { CollapseToggle(collapsed = collapsed, onToggle = onToggleCollapsed) },
        )
        totals.basket?.let { basket ->
            ExtraRow(
                label = stringResource(R.string.cart_in_basket),
                amount = context.formatCartAmount(basket, totals.destCurrency),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(visible = !collapsed) {
            Column { ExtrasBelowTotal(totals, onEditExtras) }
        }
        totals.oldRatesDate?.let { date ->
            Text(
                text = stringResource(R.string.cart_rates_from, date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = EXTRA_ROW_GAP),
            )
        }
    }
}

// Subtotal, tip and the fee line: what the collapsed card leaves out.
@Composable
private fun AboveTotal(
    totals: CartTotals,
    onEditExtras: () -> Unit,
) {
    val context = LocalContext.current
    Column {
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
    }
}

// The chevron by the total: hide the other lines, or show them again.
@Composable
private fun CollapseToggle(
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = rememberHapticOnClick(onToggle), modifier = Modifier.size(COLLAPSE_BUTTON_SIZE)) {
        Icon(
            painter = painterResource(if (collapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less),
            contentDescription = stringResource(if (collapsed) R.string.cart_totals_show_details else R.string.cart_totals_hide_details),
            modifier = Modifier.size(COLLAPSE_ICON_SIZE),
        )
    }
}

// "Per person (÷3)" and "Left in budget" / "Over budget" under the total,
// each only when set; tapping one edits them.
@Composable
private fun ExtrasBelowTotal(
    totals: CartTotals,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val extras = totals.extras
    val total = totals.total ?: BigDecimal.ZERO
    val currency = totals.destCurrency
    // Split by person: each one's name and share; otherwise the even split.
    totals.shares.forEach { (person, share) ->
        ExtraRow(
            label = person.name.ifBlank { stringResource(R.string.cart_person_unnamed) },
            amount = context.formatCartAmount(share, currency),
            onClick = onEdit,
        )
    }
    if (!extras.splitsByPerson && extras.splitWays > 1) {
        ExtraRow(
            label = stringResource(R.string.cart_per_person_row, extras.splitWays),
            amount = context.formatCartAmount(perPerson(total, extras.splitWays), currency),
            onClick = onEdit,
        )
    }
    extras.budget?.let { budget ->
        val left = budgetLeft(total, budget)
        val over = left.signum() < 0
        val color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        ExtraRow(
            label = stringResource(if (over) R.string.cart_over_budget_row else R.string.cart_budget_left_row),
            amount = context.formatCartAmount(left.abs(), currency),
            onClick = onEdit,
            color = color,
        )
        BudgetBar(used = budgetUsed(total, budget), over = over)
    }
}

// A small label + amount line for the cart's extras (tip, split, budget)
// and the basket; tapping one with [onClick] edits it.
@Composable
private fun ExtraRow(
    label: String,
    amount: String,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(top = EXTRA_ROW_GAP),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.weight(1f))
        Figure(amount, MaterialTheme.typography.labelMedium, color)
    }
}

// How much of the budget the total takes, 0 to 1 (full when over).
private fun budgetUsed(
    total: BigDecimal,
    budget: BigDecimal,
): Float = if (budget.signum() <= 0) 1f else total.divide(budget, MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f)

// The budget as a bar: the share the total takes, red once it's over.
@Composable
private fun BudgetBar(
    used: Float,
    over: Boolean,
) {
    val percent = (used * HUNDRED_PERCENT.toFloat()).roundToInt()
    val description = stringResource(R.string.cart_budget_used_a11y, percent)
    LinearProgressIndicator(
        progress = { used },
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.XSmall)
                .semantics { contentDescription = description },
    )
}

/**
 * The picker sheet for the side the user tapped: it prices each currency
 * against the other side's amount, and greys that side's currency out.
 */
@Composable
private fun CartCurrencyPickerHost(
    side: PillSide,
    totals: CartTotals,
    rates: ExchangeRates?,
    onPicked: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    val picksFrom = side == PillSide.FROM
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
    afterLabel: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = topGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label takes the room the amount leaves; [afterLabel] sits right
        // after its text, so every amount keeps the row's end edge.
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(id = labelRes), style = style, modifier = Modifier.weight(1f, fill = false))
            afterLabel?.invoke()
        }
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
    // A fee is part of the price, not a problem: muted, like the tip row.
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth().padding(top = EXTRA_ROW_GAP)) {
        Text(
            text = stripLabelSeparator(stringResource(id = prefixRes)),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Figure(valueText, MaterialTheme.typography.labelMedium, color)
    }
}

// Existing prefix strings end with a locale-specific ": " / " : " / "：" for
// inline use. Trim it here so the label sits flush left of the right column.
private fun stripLabelSeparator(text: String): String = text.trimEnd(' ', '\u00A0', ':', '：')
