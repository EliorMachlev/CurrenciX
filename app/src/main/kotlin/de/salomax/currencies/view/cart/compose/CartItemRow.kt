package de.salomax.currencies.view.cart.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.salomax.currencies.R
import de.salomax.currencies.model.CartItem
import de.salomax.currencies.util.roundForDisplay
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.viewmodel.cart.evaluateItem
import kotlinx.coroutines.delay

// Debounce so the persistence pump doesn't fire on every keystroke — the
// user typing a long item name would otherwise write once per character.
private const val NAME_EDIT_DEBOUNCE_MS = 300L

// Row previews only need two decimals; keeps the inline slot tidy even for a
// long-scale intermediate value.
private const val ROW_PREVIEW_SCALE = 2

private const val EXPRESSION_MIN_HEIGHT_DP = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun CartItemRow(
    item: CartItem,
    currency: String,
    isActive: Boolean,
    liveExpression: String?,
    onNameCommit: (String) -> Unit,
    onNamePending: (String) -> Unit,
    onExpressionTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val displayedExpression = if (isActive) liveExpression.orEmpty() else item.expression
    OutlinedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(dimensionResource(id = R.dimen.margin1x)),
        border = rowBorder(isActive),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = dimensionResource(id = R.dimen.elevation1x)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(id = R.dimen.margin1x)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                NameField(
                    initial = item.name,
                    onCommit = onNameCommit,
                    onPending = onNamePending,
                )
                ExpressionField(
                    text = displayedExpression,
                    onTap = onExpressionTap,
                )
                ValuePreview(
                    item = item.copy(expression = displayedExpression),
                    currency = currency,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(id = R.string.cart_delete_item),
                )
            }
        }
    }
}

@Composable
private fun rowBorder(isActive: Boolean): BorderStroke =
    if (isActive) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameField(
    initial: String,
    onCommit: (String) -> Unit,
    onPending: (String) -> Unit,
) {
    // Local buffer so cursor position isn't reset when the VM re-emits the
    // list. Sync only when the *external* value diverges from what we last
    // committed — a self-echo from our own updateItem must not clobber
    // in-flight typing.
    var text by remember { mutableStateOf(initial) }
    var lastCommitted by remember { mutableStateOf(initial) }
    LaunchedEffect(initial) {
        if (initial != lastCommitted && initial != text) {
            text = initial
            lastCommitted = initial
        }
    }
    LaunchedEffect(text) {
        if (text == lastCommitted) return@LaunchedEffect
        onPending(text)
        delay(NAME_EDIT_DEBOUNCE_MS)
        onCommit(text)
        lastCommitted = text
    }
    TextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(id = R.string.cart_item_name_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        colors = transparentTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExpressionField(
    text: String,
    onTap: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = EXPRESSION_MIN_HEIGHT_DP.dp)
                .clickable(onClick = onTap)
                .padding(vertical = dimensionResource(id = R.dimen.margin1x)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.ifBlank { stringResource(id = R.string.cart_item_expression_hint) },
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (text.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ValuePreview(
    item: CartItem,
    currency: String,
) {
    if (item.expression.isBlank()) return
    val context = LocalContext.current
    val formatted =
        remember(item.expression) {
            evaluateItem(item)
                .roundForDisplay(ROW_PREVIEW_SCALE)
                .toHumanReadableNumber(context, decimalPlaces = ROW_PREVIEW_SCALE)
        }
    Text(
        text = stringResource(id = R.string.cart_row_value_format, formatted, currency),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(id = R.dimen.margin1x)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun transparentTextFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )
