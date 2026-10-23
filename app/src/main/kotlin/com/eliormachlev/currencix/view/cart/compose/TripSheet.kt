package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.util.parseDecimal
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.NameInputTransformation
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.view.compose.theme.Spacing
import java.math.BigDecimal

/**
 * A new trip, or the edits to [initial]: its name, the home currency its
 * spending adds up in (one of [currencies]), and an optional budget in it.
 * Confirming hands [onSave] the name, the currency and the budget (null
 * when blank). Editing offers Delete too ([onDelete]).
 */
@Composable
fun TripSheet(
    initial: Trip?,
    currencies: List<Currency>,
    onSave: (name: String, currency: Currency, budget: BigDecimal?) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val name = rememberTextFieldState(initial?.name.orEmpty())
    val budgetField = rememberTextFieldState(initial?.budget?.toPlainString().orEmpty())
    var currency by rememberSaveable { mutableStateOf(initial?.currency ?: currencies.first()) }
    val budgetText = budgetField.text.toString()
    val budget = parseDecimal(budgetText)?.takeIf { it.signum() > 0 }
    val budgetValid = budgetText.isBlank() || budget != null
    LedgerPromptSheet(
        title = stringResource(if (initial == null) R.string.trip_new else R.string.trip_edit),
        confirmLabel = stringResource(R.string.done),
        onConfirm = { onSave(name.text.toString().trim(), currency, budget) },
        onDismiss = onDismiss,
        confirmEnabled = name.text.isNotBlank() && budgetValid,
        leadingAction =
            onDelete?.let { delete ->
                {
                    TextButton(onClick = rememberHapticOnClick(delete)) {
                        Text(stringResource(R.string.trip_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
    ) {
        OutlinedTextField(
            state = name,
            label = { Text(stringResource(R.string.trip_name)) },
            inputTransformation = NameInputTransformation,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.Medium))
        Text(stringResource(R.string.trip_currency), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            currencies.forEach { option ->
                FilterChip(
                    selected = option == currency,
                    onClick = { currency = option },
                    label = { Text(option.iso4217Alpha()) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.Small))
        OutlinedTextField(
            state = budgetField,
            label = { Text(stringResource(R.string.trip_budget)) },
            suffix = { Text(currency.iso4217Alpha()) },
            isError = !budgetValid,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.Small))
        SupportingText(stringResource(R.string.trip_rates_note))
    }
}
