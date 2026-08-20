package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.FavoriteToggleIcon
import com.eliormachlev.currencix.viewmodel.cart.evaluateItem
import kotlinx.coroutines.delay

private const val NAME_EDIT_DEBOUNCE_MS = 300L
private const val ROW_PREVIEW_SCALE = 2
private val FIELD_MIN_HEIGHT = 48.dp

// Distance from the trailing edge to the trashcan icon when the row slides.
// Matches Material's SwipeToDismiss sample so the icon reads as "emerging"
// from the row rather than pinned to the screen edge.
private val SWIPE_ICON_TRAILING_PADDING = 24.dp

/**
 * Wrap [CartItemRow] in a Material3 [SwipeToDismissBox] so a trailing-edge
 * swipe (right-to-left in LTR, left-to-right in RTL) reveals a red delete
 * background and, past the dismissal threshold, calls [onDelete] — the
 * same code path the explicit delete button uses. Rows in edit mode
 * ([isActive]) reject the gesture so the user can't wipe out a row
 * while typing into it; the existing button is left in place as an
 * always-available fallback.
 */
@Composable
@Suppress("LongParameterList")
fun SwipeableCartItemRow(
    item: CartItem,
    currency: String,
    isActive: Boolean,
    liveExpression: String?,
    onNameCommit: (String) -> Unit,
    onNamePending: (String) -> Unit,
    onExpressionTap: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeDeleteBackground(dismissState) },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isActive,
        modifier = modifier,
    ) {
        CartItemRow(
            item = item,
            currency = currency,
            isActive = isActive,
            liveExpression = liveExpression,
            onNameCommit = onNameCommit,
            onNamePending = onNamePending,
            onExpressionTap = onExpressionTap,
            onTogglePin = onTogglePin,
            dragHandleModifier = dragHandleModifier,
        )
    }
}

@Composable
private fun SwipeDeleteBackground(state: SwipeToDismissBoxState) {
    // Only paint the background once the swipe is active — otherwise the
    // OutlinedCard's ambient background would show red rectangles behind
    // every row at rest.
    val active = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(dimensionResource(id = R.dimen.margin1x))
                .background(if (active) MaterialTheme.colorScheme.error else Color.Transparent),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (active) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(id = R.string.cart_delete_item),
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.padding(end = SWIPE_ICON_TRAILING_PADDING),
            )
        }
    }
}

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
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    val displayedExpression = if (isActive) liveExpression.orEmpty() else item.expression
    OutlinedCard(
        modifier =
            modifier
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
            DragHandle(modifier = dragHandleModifier)
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
            FavoriteToggleIcon(
                active = item.pinned,
                contentDescription =
                    stringResource(
                        id = if (item.pinned) R.string.cart_unpin_item else R.string.cart_pin_item,
                    ),
                onClick = onTogglePin,
            )
        }
    }
}

@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.DragHandle,
        contentDescription = stringResource(id = R.string.cart_reorder_item),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(end = dimensionResource(id = R.dimen.margin1x)),
    )
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
    val fieldLabel = stringResource(id = R.string.a11y_cart_item_name)
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
            // BasicTextField has no visible label and the hint disappears once
            // the user types, leaving TalkBack focused on an unlabelled field.
            // Attach a persistent contentDescription so it always announces as
            // "Item name".
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = fieldLabel },
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
