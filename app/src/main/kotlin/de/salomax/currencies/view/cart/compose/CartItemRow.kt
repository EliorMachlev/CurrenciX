package de.salomax.currencies.view.cart.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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

private const val NAME_EDIT_DEBOUNCE_MS = 300L
private const val ROW_PREVIEW_SCALE = 2
private val FIELD_MIN_HEIGHT = 28.dp

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
    val textStyle =
        LocalTextStyle.current.merge(
            MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        )
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = FIELD_MIN_HEIGHT),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        if (text.isEmpty()) {
            Text(
                text = stringResource(id = R.string.cart_item_name_hint),
                style = textStyle.copy(color = hintColor),
            )
        }
    }
}

@Composable
private fun ExpressionField(
    text: String,
    onTap: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = FIELD_MIN_HEIGHT)
                .clickable(onClick = onTap),
        contentAlignment = Alignment.CenterStart,
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
