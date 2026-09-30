package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import java.math.BigDecimal

private val CONTENT_PADDING = 16.dp
private val GAP = 16.dp
private val CHIP_GAP = 8.dp
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
    var tip by rememberSaveable { mutableStateOf(initial.tipPercent?.toPlainString().orEmpty()) }
    var split by rememberSaveable { mutableIntStateOf(initial.splitWays) }
    var budget by rememberSaveable { mutableStateOf(initial.budget?.toPlainString().orEmpty()) }
    val tipValue = remember(tip) { parseDecimal(tip)?.takeIf { it.signum() > 0 && it <= MAX_TIP } }
    val budgetValue = remember(budget) { parseDecimal(budget)?.takeIf { it.signum() > 0 } }
    LedgerBottomSheet(title = stringResource(R.string.cart_menu_extras), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(CONTENT_PADDING), verticalArrangement = Arrangement.spacedBy(GAP)) {
            TipField(tip, isError = tip.isNotBlank() && tipValue == null, onChange = { tip = it })
            SplitStepper(split, onChange = { split = it })
            OutlinedTextField(
                value = budget,
                onValueChange = { budget = it },
                label = { Text(stringResource(R.string.cart_budget_label)) },
                prefix = destination?.let { { Text(it.symbolOrIso() + " ") } },
                isError = budget.isNotBlank() && budgetValue == null,
                singleLine = true,
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
    value: String,
    isError: Boolean,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(R.string.cart_tip_label)) },
            suffix = { Text("%") },
            isError = isError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
            QUICK_TIPS.forEach { percent ->
                FilterChip(
                    selected = value == percent,
                    onClick = { onChange(if (value == percent) "" else percent) },
                    label = { Text("$percent%") },
                )
            }
        }
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
