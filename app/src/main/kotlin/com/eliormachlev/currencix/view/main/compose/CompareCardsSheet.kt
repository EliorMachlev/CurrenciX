package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CardCost
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.util.ltrIsolate
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.PromptMessage
import com.eliormachlev.currencix.view.compose.theme.Spacing
import java.math.RoundingMode

private val CHECK_SIZE = 20.dp

/**
 * "Which card is cheaper": each bank / card fee ([costs], cheapest first)
 * with its whole fee stack and, when there's an amount, what it comes to in
 * [currency] and how much more than the cheapest. The card in use is ticked;
 * tapping another picks it ([onPick]).
 */
@Composable
internal fun CompareCardsSheet(
    costs: List<CardCost>,
    currency: Currency?,
    activeCardId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // The card in force: the picked one, or the first (as FeeCalculator falls back).
    val inUse = activeCardId?.takeIf { id -> costs.any { it.card.id == id } } ?: costs.firstOrNull()?.card?.id
    LedgerBottomSheet(title = stringResource(R.string.compare_cards_title), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.XXLarge, vertical = Spacing.Small)) {
            if (costs.firstOrNull()?.total == null) PromptMessage(stringResource(R.string.compare_enter_amount))
            costs.forEach { cost ->
                CardRow(
                    cost = cost,
                    cheapest = costs.first(),
                    inUse = cost.card.id == inUse,
                    currency = currency,
                    onClick = { onPick(cost.card.id) },
                )
            }
            SupportingText(stringResource(R.string.compare_pick_hint))
        }
    }
}

// "Revolut  +0.50%   410.25 ₪   Cheapest" / "Visa abroad  +2.50%   418.42 ₪   +8.17 ₪ more"
@Composable
private fun CardRow(
    cost: CardCost,
    cheapest: CardCost,
    inUse: Boolean,
    currency: Currency?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val isCheapest = cost.card.id == cheapest.card.id
    val percent = "+" + cost.stack.feePercentDelta(roundingMode = RoundingMode.HALF_UP).toPlainString() + "%"
    val name = cost.card.name.ifBlank { percent }
    val extra = cost.total?.let { total -> cheapest.total?.let { total.subtract(it) } }
    val note =
        when {
            isCheapest -> {
                stringResource(R.string.compare_cheapest)
            }

            extra != null && extra.signum() > 0 -> {
                stringResource(
                    R.string.compare_more,
                    ltrIsolate(context.formatCartAmount(extra, currency)),
                )
            }

            else -> {
                null
            }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = inUse, role = Role.RadioButton, onClick = rememberHapticOnClick(onClick))
                .padding(vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (inUse) FontWeight.SemiBold else null)
            Ltr { SupportingText(percent) }
        }
        Column(horizontalAlignment = Alignment.End) {
            cost.total?.let { Ltr { Text(context.formatCartAmount(it, currency), style = MaterialTheme.typography.bodyLarge) } }
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCheapest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (inUse) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = stringResource(R.string.compare_in_use),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(CHECK_SIZE),
            )
        }
    }
}
