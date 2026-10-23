package com.eliormachlev.currencix.view.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

private const val PERCENT_SIGN = "%"

/**
 * An outlined field for a percentage, laid out as in a left-to-right
 * language in every language: the number from the left, and the "%" at the
 * field's right edge (Material's suffix), not beside the number. In Hebrew
 * the suffix would otherwise sit at the left edge.
 */
@Composable
fun PercentTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    label: String? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
) {
    Ltr {
        OutlinedTextField(
            state = state,
            label = label?.let { { Text(it) } },
            suffix = { Text(PERCENT_SIGN, style = MaterialTheme.typography.bodyLarge) },
            isError = isError,
            inputTransformation = inputTransformation,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = modifier.fillMaxWidth(),
        )
    }
}
