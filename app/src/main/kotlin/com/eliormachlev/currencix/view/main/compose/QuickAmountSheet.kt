package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.isQuickAmount
import com.eliormachlev.currencix.util.parseDecimal
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.view.compose.fieldMessage
import com.eliormachlev.currencix.view.compose.theme.Spacing
import java.math.BigDecimal

/**
 * A quick-conversion tile's new amount in [currency], starting from
 * [initial]: above zero and not one of the other tiles' ([taken]). Saved for
 * the currency in every pair, in place of the automatic amounts.
 */
@Composable
internal fun QuickAmountSheet(
    currency: Currency,
    initial: BigDecimal,
    taken: List<BigDecimal>,
    onSave: (BigDecimal) -> Unit,
    onDismiss: () -> Unit,
) {
    val field = rememberTextFieldState(initial.stripTrailingZeros().toPlainString())
    val text = field.text.toString()
    val amount = parseDecimal(text)
    val error =
        when {
            text.isBlank() -> null
            amount == null || !isQuickAmount(amount) -> stringResource(R.string.error_positive_number)
            taken.any { it.compareTo(amount) == 0 } -> stringResource(R.string.quick_amount_taken)
            else -> null
        }
    LedgerPromptSheet(
        title = stringResource(R.string.quick_amount_change),
        confirmLabel = stringResource(R.string.done),
        onConfirm = { amount?.let(onSave) },
        onDismiss = onDismiss,
        confirmEnabled = amount != null && error == null,
    ) {
        OutlinedTextField(
            state = field,
            label = { Text(stringResource(R.string.quick_amount_label)) },
            suffix = { Text(currency.iso4217Alpha()) },
            isError = error != null,
            supportingText = fieldMessage(error),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.Small))
        SupportingText(stringResource(R.string.quick_amount_note, currency.iso4217Alpha()))
    }
}
