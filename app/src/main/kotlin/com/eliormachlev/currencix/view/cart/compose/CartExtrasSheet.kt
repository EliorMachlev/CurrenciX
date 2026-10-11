package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.ConnectedToggleGroup
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.theme.Spacing
import java.math.BigDecimal

private val CONTENT_PADDING = Spacing.Large
private val GAP = Spacing.Large
private val TIP_FIELD_GAP = Spacing.Small
private val SPLIT_COUNT_WIDTH = 40.dp

// One-tap tip / tax rates; any other goes in the field.
private val QUICK_TIPS = listOf("10", "15", "20")

// Tip / tax as a percentage, 0 < tip ≤ 100.
private val MAX_TIP = BigDecimal(100)

/**
 * Edits a cart's [CartExtras]: tip / tax percentage, how many people split
 * the total, and a budget in the destination currency. Empty fields mean
 * "none"; Done saves them all at once.
 */
@Composable
fun CartExtrasSheet(
    initial: CartExtras,
    destination: Currency?,
    onDone: (CartExtras) -> Unit,
    onDismiss: () -> Unit,
) {
    val tipState = rememberTextFieldState(initial.tipPercent?.toPlainString().orEmpty())
    var split by rememberSaveable { mutableIntStateOf(initial.splitWays) }
    val budgetState = rememberTextFieldState(initial.budget?.toPlainString().orEmpty())
    val tip = tipState.text.toString()
    val budget = budgetState.text.toString()
    val tipValue = remember(tip) { parseDecimal(tip)?.takeIf { it.signum() > 0 && it <= MAX_TIP } }
    val budgetValue = remember(budget) { parseDecimal(budget)?.takeIf { it.signum() > 0 } }
    LedgerBottomSheet(title = stringResource(R.string.cart_menu_extras), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(CONTENT_PADDING), verticalArrangement = Arrangement.spacedBy(GAP)) {
            TipField(tipState, isError = tip.isNotBlank() && tipValue == null)
            SplitStepper(split, onChange = { split = it })
            OutlinedTextField(
                state = budgetState,
                label = { Text(stringResource(R.string.cart_budget_label)) },
                prefix = destination?.let { { Text(it.symbolOrIso() + " ") } },
                isError = budget.isNotBlank() && budgetValue == null,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(
                onClick = rememberHapticOnClick { onDone(CartExtras(tipValue, split, budgetValue)) },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.done)) }
        }
    }
}

@Composable
private fun TipField(
    state: TextFieldState,
    isError: Boolean,
) {
    val value = state.text.toString()
    Column(verticalArrangement = Arrangement.spacedBy(TIP_FIELD_GAP)) {
        OutlinedTextField(
            state = state,
            label = { Text(stringResource(R.string.cart_tip_label)) },
            suffix = { Text("%") },
            isError = isError,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        // A quick rate fills the field; tapping it again clears it.
        ConnectedToggleGroup(
            options = QUICK_TIPS,
            selected = value.takeIf { it in QUICK_TIPS },
            label = { "$it%" },
            onToggle = { percent, checked -> state.setTextAndPlaceCursorAtEnd(if (checked) percent else "") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// "Split between   (−)  3  (+)"
@Composable
private fun SplitStepper(
    ways: Int,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.cart_split_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = rememberHapticOnClick { onChange(ways - 1) }, enabled = ways > 1) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.cart_split_decrease))
        }
        Text(
            ways.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = SPLIT_COUNT_WIDTH),
        )
        FilledTonalIconButton(onClick = rememberHapticOnClick { onChange(ways + 1) }, enabled = ways < CartExtras.MAX_SPLIT) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.cart_split_increase))
        }
    }
}

// "12,5" and "12.5" alike; blank or unreadable is null.
internal fun parseDecimal(text: String): BigDecimal? =
    text
        .trim()
        .replace(',', '.')
        .takeIf { it.isNotEmpty() }
        ?.toBigDecimalOrNull()
