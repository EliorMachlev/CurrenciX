package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hintText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.OPERATOR_DIVIDE
import com.eliormachlev.currencix.util.OPERATOR_MINUS
import com.eliormachlev.currencix.util.OPERATOR_MULTIPLY
import com.eliormachlev.currencix.util.OPERATOR_PLUS
import com.eliormachlev.currencix.util.PAREN_OPEN
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.hapticOnFocus
import com.eliormachlev.currencix.view.compose.FavoriteToggleIcon
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.NameInputTransformation
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.viewmodel.cart.RowConversion
import com.eliormachlev.currencix.viewmodel.cart.evaluatePrice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val NAME_EDIT_DEBOUNCE_MS = 300L
private val FIELD_MIN_HEIGHT = 48.dp

// Rows of one section share a card; between rows, a hairline inset to
// where the text starts (past the drag handle).
private val ROW_CORNER = 14.dp
private val DIVIDER_INSET = 40.dp
private const val ACTIVE_ROW_TINT = 0.08f

// A price that's worked out ("3.25 + 0.50", "2.99 × 2"), as opposed to a
// plain amount: the calculation is shown under the result.
private val CALCULATION_CHARS = (OPERATOR_PLUS + OPERATOR_MINUS + OPERATOR_MULTIPLY + OPERATOR_DIVIDE + PAREN_OPEN + "%").toSet()

// Distance from the swipe-origin edge to the trashcan icon when the row
// slides. Matches Material's SwipeToDismiss sample so the icon reads as
// "emerging" from the row rather than pinned to the screen edge — applied
// to whichever side the user is dragging from.
private val SWIPE_ICON_EDGE_PADDING = Spacing.XXLarge

/** What one cart row shows. */
@Immutable
data class CartRowState(
    val item: CartItem,
    val currency: String,
    // The expression as it's being typed, while this row is the keypad's
    // target; null for a row that isn't being edited.
    val liveExpression: String? = null,
    // The price in the destination currency; null when there's nothing to convert to.
    val conversion: RowConversion? = null,
) {
    val isActive: Boolean get() = liveExpression != null
    val displayedExpression: String get() = liveExpression ?: item.expression
}

/**
 * Where a row sits in its section's card: the first rounds its top
 * corners, the last its bottom ones, and every row but the first has a
 * divider above it.
 */
enum class RowPosition(
    top: Boolean,
    bottom: Boolean,
) {
    Single(top = true, bottom = true),
    First(top = true, bottom = false),
    Middle(top = false, bottom = false),
    Last(top = false, bottom = true),
    ;

    val shape: Shape =
        RoundedCornerShape(
            topStart = if (top) ROW_CORNER else 0.dp,
            topEnd = if (top) ROW_CORNER else 0.dp,
            bottomStart = if (bottom) ROW_CORNER else 0.dp,
            bottomEnd = if (bottom) ROW_CORNER else 0.dp,
        )

    val hasDividerAbove: Boolean = !top

    companion object {
        /** The position of row [index] among [count] rows. */
        fun of(
            index: Int,
            count: Int,
        ): RowPosition =
            when {
                count == 1 -> Single
                index == 0 -> First
                index == count - 1 -> Last
                else -> Middle
            }
    }
}

/** What one cart row can ask for. */
@Immutable
class CartRowActions(
    val onNameCommit: (String) -> Unit,
    val onNamePending: (String) -> Unit,
    val onExpressionTap: () -> Unit,
    val onTogglePin: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * Wrap [CartItemRow] in a Material3 [SwipeToDismissBox] so a swipe in either
 * direction reveals a red delete background and, past the dismissal
 * threshold, deletes the row. A row in edit mode rejects the gesture so the
 * user can't wipe out a row while typing into it.
 */
@Composable
fun SwipeableCartItemRow(
    state: CartRowState,
    actions: CartRowActions,
    modifier: Modifier = Modifier,
    position: RowPosition = RowPosition.Single,
    dragHandleModifier: Modifier = Modifier,
) {
    // Observe currentValue rather than passing confirmValueChange (deprecated —
    // the anchor set already excludes disallowed sides via
    // enableDismissFromStart/EndTo…). onDelete removes the row from the source
    // list, which drops this SwipeToDismissBox from composition and lets the
    // LazyList's animateItem() handle the slide-out.
    val dismissState = rememberSwipeToDismissBoxState()
    val onDeleteState = rememberUpdatedState(actions.onDelete)
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.currentValue }.collect { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd ||
                value == SwipeToDismissBoxValue.EndToStart
            ) {
                onDeleteState.value()
            }
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeDeleteBackground(dismissState, position.shape) },
        enableDismissFromStartToEnd = !state.isActive,
        enableDismissFromEndToStart = !state.isActive,
        modifier = modifier,
    ) {
        CartItemRow(state = state, actions = actions, position = position, dragHandleModifier = dragHandleModifier)
    }
}

@Composable
private fun SwipeDeleteBackground(
    state: SwipeToDismissBoxState,
    shape: Shape,
) {
    // Only paint the background once the swipe is active — otherwise red
    // would show through behind every row at rest.
    val direction = state.dismissDirection
    val active =
        direction == SwipeToDismissBoxValue.StartToEnd ||
            direction == SwipeToDismissBoxValue.EndToStart
    // Anchor the trash icon on whichever edge the finger is dragging *from*
    // (so it appears to emerge from under the row) rather than pinning it
    // to a single side regardless of swipe direction.
    val alignment =
        if (direction == SwipeToDismissBoxValue.StartToEnd) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // Same corners as the row sliding off it.
                .clip(shape)
                .background(if (active) MaterialTheme.colorScheme.error else Color.Transparent),
        contentAlignment = alignment,
    ) {
        if (active) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(id = R.string.cart_delete_item),
                tint = MaterialTheme.colorScheme.onError,
                modifier =
                    Modifier.padding(
                        start =
                            if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                SWIPE_ICON_EDGE_PADDING
                            } else {
                                0.dp
                            },
                        end =
                            if (direction == SwipeToDismissBoxValue.EndToStart) {
                                SWIPE_ICON_EDGE_PADDING
                            } else {
                                0.dp
                            },
                    ),
            )
        }
    }
}

/**
 * One cart item as a receipt line: the name, editable in place, and the
 * price at the end (tap it for the keypad); under them the calculation, when
 * the price is worked out, and the price in the destination currency. Rows
 * of a section share a card ([position]); the row being edited is tinted.
 */
@Composable
fun CartItemRow(
    state: CartRowState,
    actions: CartRowActions,
    modifier: Modifier = Modifier,
    position: RowPosition = RowPosition.Single,
    dragHandleModifier: Modifier = Modifier,
) {
    val item = state.item
    val surface = MaterialTheme.colorScheme.surface
    val background =
        if (state.isActive) {
            MaterialTheme.colorScheme.primary
                .copy(alpha = ACTIVE_ROW_TINT)
                .compositeOver(surface)
        } else {
            surface
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(position.shape)
                .background(background),
    ) {
        if (position.hasDividerAbove) {
            HorizontalDivider(Modifier.padding(start = DIVIDER_INSET), color = MaterialTheme.colorScheme.outlineVariant)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.Tiny, top = Spacing.Tiny, bottom = Spacing.Tiny),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DragHandle(modifier = dragHandleModifier)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        NameField(
                            initial = item.name,
                            onCommit = actions.onNameCommit,
                            onPending = actions.onNamePending,
                        )
                    }
                    PriceField(state = state, onTap = actions.onExpressionTap)
                }
                PriceDetails(state = state, onTap = actions.onExpressionTap)
            }
            FavoriteToggleIcon(
                active = item.pinned,
                contentDescription =
                    stringResource(
                        id = if (item.pinned) R.string.cart_unpin_item else R.string.cart_pin_item,
                    ),
                onClick = actions.onTogglePin,
            )
        }
    }
}

// The row's price at its end edge: the result in the cart's currency, or a
// "Price" placeholder before one is entered. Tapping opens the keypad.
@Composable
private fun PriceField(
    state: CartRowState,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val expression = state.displayedExpression
    val price =
        remember(expression, state.currency) {
            expression.takeIf { it.isNotBlank() }?.let { context.formatCartAmount(evaluatePrice(it), Currency.fromString(state.currency)) }
        }
    Box(
        modifier =
            Modifier
                .defaultMinSize(minHeight = FIELD_MIN_HEIGHT)
                .hapticClickable(onClick = onTap)
                .padding(start = Spacing.Small),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (price == null) {
            Text(
                text = stringResource(id = R.string.cart_item_price_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // An amount reads left to right in every language.
            Ltr { Text(text = price, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// Under the name and price: the calculation (when the price is worked out)
// at the start, the price in the destination currency at the end. Nothing
// when there's neither.
@Composable
private fun PriceDetails(
    state: CartRowState,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val expression = state.displayedExpression
    val calculation = expression.takeIf { it.any(CALCULATION_CHARS::contains) }
    val convertedAmount =
        remember(expression, state.conversion) {
            val conversion = state.conversion ?: return@remember null
            expression
                .takeIf { it.isNotBlank() }
                ?.let { conversion.convert(evaluatePrice(it)) }
                ?.let { context.formatCartAmount(it, conversion.currency) }
        }
    val converted = convertedAmount?.let { stringResource(R.string.cart_row_converted, it) }
    if (calculation == null && converted == null) return
    Row(
        modifier = Modifier.fillMaxWidth().hapticClickable(onClick = onTap).padding(bottom = Spacing.Tiny),
        // A long calculation wraps; the converted price stays level with its first line.
        verticalAlignment = Alignment.Top,
    ) {
        // Kept apart from the converted price when a long calculation wraps.
        Box(Modifier.weight(1f).padding(end = Spacing.Small)) {
            calculation?.let { Calculation(it) }
        }
        converted?.let { Ltr { SupportingText(it) } }
    }
}

// A price's calculation ("2 × 3.50"): math, so left to right in every
// language, but its lines on the side the language starts reading from.
@Composable
private fun Calculation(text: String) {
    val align = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
    Ltr {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_drag_handle),
        contentDescription = stringResource(id = R.string.cart_reorder_item),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.Small),
    )
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
    val state = remember { TextFieldState(initial) }
    var lastCommitted by remember { mutableStateOf(initial) }
    LaunchedEffect(initial) {
        if (initial != lastCommitted && initial != state.text.toString()) {
            state.setTextAndPlaceCursorAtEnd(initial)
            lastCommitted = initial
        }
    }
    val pending by rememberUpdatedState(onPending)
    val commit by rememberUpdatedState(onCommit)
    // Text edits only (not cursor moves); a new keystroke restarts the wait.
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collectLatest { text ->
            if (text == lastCommitted) return@collectLatest
            pending(text)
            delay(NAME_EDIT_DEBOUNCE_MS)
            commit(text)
            lastCommitted = text
        }
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
            state = state,
            inputTransformation = NameInputTransformation,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            // BasicTextField has no visible label, and the placeholder below
            // goes once the user types: TalkBack reads "Item name" as the
            // field's hint, after whatever is typed, so it's always named.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .hapticOnFocus()
                    .semantics { hintText = fieldLabel },
        )
        if (state.text.isEmpty()) {
            // The hint above already says it; not read a second time.
            Text(
                text = stringResource(id = R.string.cart_item_name_hint),
                style = textStyle.copy(color = hintColor),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
