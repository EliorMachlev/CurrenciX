package com.eliormachlev.currencix.view.cart.compose

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hintText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.CART_EXPORT_DISPLAY_SCALE
import com.eliormachlev.currencix.util.OPERATOR_DIVIDE
import com.eliormachlev.currencix.util.OPERATOR_MINUS
import com.eliormachlev.currencix.util.OPERATOR_MULTIPLY
import com.eliormachlev.currencix.util.OPERATOR_PLUS
import com.eliormachlev.currencix.util.PAREN_OPEN
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.hapticOnFocus
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.FavoriteToggleIcon
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.WrappingNameInputTransformation
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.viewmodel.cart.RowConversion
import com.eliormachlev.currencix.viewmodel.cart.evaluateItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.math.BigDecimal

private const val NAME_EDIT_DEBOUNCE_MS = 300L
private val FIELD_MIN_HEIGHT = 48.dp

// Rows of one section share a card; between rows, a hairline inset to
// where the text starts (past the drag handle).
private val ROW_CORNER = 14.dp
private val DIVIDER_INSET = 40.dp
private const val ACTIVE_ROW_TINT = 0.08f
private const val CHECKED_ROW_ALPHA = 0.55f
private val STEP_BUTTON_SIZE = 32.dp
private val STEP_ICON_SIZE = 18.dp

// Between the parts of a row's spoken price: TalkBack pauses at a comma.
private const val SPOKEN_PART_SEPARATOR = ", "

// A long name wraps up to this many lines, then scrolls inside the field.
private const val NAME_MAX_LINES = 3

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
    // Shopping-list mode: a tick for "in the basket" instead of the drag handle.
    val shoppingMode: Boolean = false,
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
    val onQuantityChange: (Int) -> Unit = {},
    val onToggleChecked: () -> Unit = {},
    val onDuplicate: () -> Unit = {},
    val onMoveToTop: () -> Unit = {},
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
 * the price is worked out or bought more than once, and the price in the
 * destination currency. Rows of a section share a card ([position]); the
 * row being edited is tinted and shows a quantity stepper. In shopping-list
 * mode a tick marks the item as in the basket. Long-press for duplicate,
 * move to top and delete.
 */
@Composable
fun CartItemRow(
    state: CartRowState,
    actions: CartRowActions,
    modifier: Modifier = Modifier,
    position: RowPosition = RowPosition.Single,
    dragHandleModifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val rowActions = rowAccessibilityActions(actions)
    Box(modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(position.shape)
                    .background(rowBackground(state.isActive))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuOpen = true
                            },
                        )
                    }.semantics { customActions = rowActions },
        ) {
            if (position.hasDividerAbove) {
                HorizontalDivider(Modifier.padding(start = DIVIDER_INSET), color = MaterialTheme.colorScheme.outlineVariant)
            }
            RowContent(state = state, actions = actions) {
                if (state.shoppingMode) {
                    BasketCheckbox(checked = state.item.checked, onToggle = actions.onToggleChecked)
                } else {
                    DragHandle(modifier = dragHandleModifier)
                }
            }
        }
        RowMenu(expanded = menuOpen, actions = actions, onDismiss = { menuOpen = false })
    }
}

// The line itself: [leading] (the tick or the drag handle), the name and
// price with the details under them, and the pin.
@Composable
private fun RowContent(
    state: CartRowState,
    actions: CartRowActions,
    leading: @Composable () -> Unit,
) {
    val item = state.item
    val inBasket = state.shoppingMode && item.checked
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.Tiny, top = Spacing.Tiny, bottom = Spacing.Tiny)
                .alpha(if (inBasket) CHECKED_ROW_ALPHA else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    NameField(
                        initial = item.name,
                        onCommit = actions.onNameCommit,
                        onPending = actions.onNamePending,
                        struck = inBasket,
                    )
                }
                PriceField(state = state, onTap = actions.onExpressionTap)
            }
            PriceDetails(state = state, actions = actions)
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

// The edited row is tinted with the accent; the rest sit on the card color.
@Composable
private fun rowBackground(active: Boolean): Color {
    val surface = MaterialTheme.colorScheme.surface
    return if (active) {
        MaterialTheme.colorScheme.primary
            .copy(alpha = ACTIVE_ROW_TINT)
            .compositeOver(surface)
    } else {
        surface
    }
}

// The long-press menu's actions, also offered to TalkBack as the row's actions.
@Composable
private fun rowAccessibilityActions(actions: CartRowActions): List<CustomAccessibilityAction> {
    val duplicate = stringResource(R.string.cart_duplicate_item)
    val moveToTop = stringResource(R.string.cart_move_to_top)
    val delete = stringResource(R.string.cart_delete_item)
    return remember(actions, duplicate, moveToTop, delete) {
        listOf(
            CustomAccessibilityAction(duplicate) { true.also { actions.onDuplicate() } },
            CustomAccessibilityAction(moveToTop) { true.also { actions.onMoveToTop() } },
            CustomAccessibilityAction(delete) { true.also { actions.onDelete() } },
        )
    }
}

// Duplicate, move to top, delete: the long-press menu.
@Composable
private fun RowMenu(
    expanded: Boolean,
    actions: CartRowActions,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf(
            Triple(R.string.cart_duplicate_item, R.drawable.ic_content_copy, actions.onDuplicate),
            Triple(R.string.cart_move_to_top, R.drawable.ic_vertical_align_top, actions.onMoveToTop),
            Triple(R.string.cart_delete_item, R.drawable.ic_delete, actions.onDelete),
        ).forEach { (label, icon, action) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                leadingIcon = { Icon(painter = painterResource(icon), contentDescription = null) },
                onClick =
                    rememberHapticOnClick {
                        onDismiss()
                        action()
                    },
            )
        }
    }
}

// Shopping-list mode's tick: the item is in the basket.
@Composable
private fun BasketCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val label = stringResource(R.string.cart_in_basket_item)
    Checkbox(
        checked = checked,
        onCheckedChange = { onToggle() },
        modifier = Modifier.semantics { contentDescription = label },
    )
}

// The row's price at its end edge: the result in the cart's currency (its
// quantity included), or a "Price" placeholder before one is entered.
// Tapping opens the keypad; TalkBack hears the whole price in one go.
@Composable
private fun PriceField(
    state: CartRowState,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val total = state.total()
    val currency = Currency.fromString(state.currency)
    val price = remember(total, currency) { total?.let { context.formatCartAmount(it, currency) } }
    val spoken = spokenPrice(state, total, currency)
    Box(
        modifier =
            Modifier
                .defaultMinSize(minHeight = FIELD_MIN_HEIGHT)
                .hapticClickable(onClick = onTap)
                .padding(start = Spacing.Small)
                .semantics { spoken?.let { contentDescription = it } },
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

// The price as TalkBack reads it: "4.50 US dollar, quantity 2, about 16.42
// Israeli new shekel" — currency names, not symbols. Null before a price.
@Composable
private fun spokenPrice(
    state: CartRowState,
    total: BigDecimal?,
    currency: Currency?,
): String? {
    total ?: return null
    val amount = spokenAmount(total, currency)
    val quantity =
        state.item.quantity
            .takeIf { it > 1 }
            ?.let { stringResource(R.string.cart_row_a11y_quantity, it) }
    val converted =
        state.conversion?.let { conversion ->
            conversion.convert(total)?.let { stringResource(R.string.cart_row_a11y_converted, spokenAmount(it, conversion.currency)) }
        }
    return listOfNotNull(amount, quantity, converted).joinToString(SPOKEN_PART_SEPARATOR)
}

// "4.50 US dollar": the amount with its currency's name, for TalkBack.
@Composable
private fun spokenAmount(
    amount: BigDecimal,
    currency: Currency?,
): String {
    val context = LocalContext.current
    val number =
        remember(amount) {
            amount.roundForDisplay(CART_EXPORT_DISPLAY_SCALE).toHumanReadableNumber(context, decimalPlaces = CART_EXPORT_DISPLAY_SCALE)
        }
    val name = currency?.let { stringResource(it.fullNameRes) }.orEmpty()
    return stringResource(R.string.cart_row_a11y_amount, number, name).trim()
}

// The row's price with its quantity, live while it's being typed; null before a price.
private fun CartRowState.total(): BigDecimal? =
    displayedExpression.takeIf { it.isNotBlank() }?.let { evaluateItem(item.copy(expression = it)) }

// Under the name and price: the calculation (when the price is worked out
// or bought more than once) at the start, and the price in the destination
// currency at the end; on the row being edited, a quantity stepper first.
// TalkBack reads all of it with the price ([spokenPrice]).
@Composable
private fun PriceDetails(
    state: CartRowState,
    actions: CartRowActions,
) {
    val calculation = state.calculation()
    val converted = convertedPrice(state)
    if (calculation == null && converted == null && !state.isActive) return
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.Tiny), verticalAlignment = Alignment.CenterVertically) {
        if (state.isActive) QuantityStepper(quantity = state.item.quantity, onChange = actions.onQuantityChange)
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .hapticClickable(onClick = actions.onExpressionTap)
                    .clearAndSetSemantics {},
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
}

// The calculation under the price: when it's worked out ("3.25 + 0.50") or
// bought more than once ("4.50 × 3"); null for a plain price. While the row
// is edited the stepper shows the quantity, so it's left out here.
private fun CartRowState.calculation(): String? {
    val expression = displayedExpression.takeIf { it.isNotBlank() } ?: return null
    if (item.quantity == 1 && expression.none(CALCULATION_CHARS::contains)) return null
    return if (isActive) expression else item.copy(expression = expression).expressionWithQuantity
}

// "≈ 16.42 ₪": the row's price in the destination currency; null with
// nothing to convert to or no price yet.
@Composable
private fun convertedPrice(state: CartRowState): String? {
    val context = LocalContext.current
    val total = state.total()
    val amount =
        remember(total, state.conversion) {
            val conversion = state.conversion ?: return@remember null
            total?.let { conversion.convert(it) }?.let { context.formatCartAmount(it, conversion.currency) }
        }
    return amount?.let { stringResource(R.string.cart_row_converted, it) }
}

// "−  ×2  +" on the row being edited.
@Composable
private fun QuantityStepper(
    quantity: Int,
    onChange: (Int) -> Unit,
) {
    Ltr {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = Spacing.Small)) {
            StepButton(R.drawable.ic_remove, R.string.cart_quantity_decrease, enabled = quantity > CartItem.MIN_QUANTITY) {
                onChange(quantity - 1)
            }
            val spoken = stringResource(R.string.cart_row_a11y_quantity, quantity)
            Text(
                text = stringResource(R.string.cart_quantity_value, quantity),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { contentDescription = spoken },
            )
            StepButton(R.drawable.ic_add, R.string.cart_quantity_increase, enabled = quantity < CartItem.MAX_QUANTITY) {
                onChange(quantity + 1)
            }
        }
    }
}

@Composable
private fun StepButton(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = rememberHapticOnClick(onClick), enabled = enabled, modifier = Modifier.size(STEP_BUTTON_SIZE)) {
        Icon(painter = painterResource(icon), contentDescription = stringResource(description), modifier = Modifier.size(STEP_ICON_SIZE))
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
    struck: Boolean = false,
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
            MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                // In the basket: crossed off, as on a paper list.
                textDecoration = if (struck) TextDecoration.LineThrough else null,
            ),
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
            // A long name wraps rather than scrolling out of sight; it stays one paragraph.
            inputTransformation = WrappingNameInputTransformation,
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = NAME_MAX_LINES),
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
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
