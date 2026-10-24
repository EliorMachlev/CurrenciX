package com.eliormachlev.currencix.view.main.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.DISABLED_ROW_ALPHA
import com.eliormachlev.currencix.util.DOUBLE_ZERO
import com.eliormachlev.currencix.util.PAREN_CLOSE
import com.eliormachlev.currencix.util.PAREN_OPEN
import com.eliormachlev.currencix.util.TRIPLE_ZERO
import com.eliormachlev.currencix.util.ZERO
import com.eliormachlev.currencix.util.getDecimalSeparator
import com.eliormachlev.currencix.util.hapticCombinedClickable
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.UiTestTags
import com.eliormachlev.currencix.view.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.view.compose.rememberDatabase
import com.eliormachlev.currencix.view.compose.theme.Motion
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.viewmodel.main.KeyAvailability
import com.eliormachlev.currencix.viewmodel.main.Operator

// Rows are [KEY_ROW_HEIGHT] tall when there's room and squeeze down to
// [KEY_ROW_MIN_HEIGHT] (still a full touch target) when the display above
// needs it — a long hero card or the recent pairs under it would otherwise
// end up behind the keypad. They never grow past KEY_ROW_HEIGHT: on a tall
// phone the spare room goes to the display, not to bigger keys.
private val KEY_ROW_HEIGHT: Dp = 56.dp
private val KEY_ROW_MIN_HEIGHT: Dp = 48.dp
private const val BASIC_ROWS = 5
private const val EXPANDED_ROWS = 6
private val KEY_TEXT_SIZE = 26.sp
private val OPERATOR_TEXT_SIZE = 30.sp
private val KEYPAD_VERTICAL_PADDING: Dp = Spacing.Tiny
private val KEYPAD_HORIZONTAL_PADDING: Dp = Spacing.Tiny
private val DELETE_ICON_SIZE: Dp = 24.dp

// The gap around every key, and their corners: tiles, not floating text.
private val KEY_GAP: Dp = Spacing.Tiny / 2
private val KEY_CORNER: Dp = 16.dp
private val RAIL_SHAPE = RoundedCornerShape(24.dp)
private val EQUALS_SHAPE = RoundedCornerShape(20.dp)

// The digits take three columns, the operator rail one.
private const val DIGIT_COLUMNS = 3f

// The = key is two rows tall at the rail's foot; the four operators share
// the rest of the rail evenly.
private const val EQUALS_ROWS = 2
private const val OPERATOR_COUNT = 4

// A pressed key gives a little under the finger, and its corners round.
private const val PRESSED_SCALE = 0.94f
private val PRESSED_CORNER: Dp = 28.dp

// On a screen at least this wide (an unfolded foldable, a tablet) the
// keypad keeps to one side, ONE_HANDED_WIDTH wide, in reach of one thumb.
private val ONE_HANDED_MIN_WIDTH: Dp = 600.dp
private val ONE_HANDED_WIDTH: Dp = 420.dp

// The split bracket key's divider: a fraction of the key's height.
private const val SPLIT_DIVIDER_HEIGHT = 0.45f
private val SPLIT_DIVIDER_WIDTH: Dp = 2.dp

/** How tall the keypad is: [full] when there's room, down to [compact] when the display needs it. */
@Immutable
data class KeypadHeights(
    val compact: Dp,
    val full: Dp,
)

/** The [KeypadHeights] of the basic (5-row) or expanded (6-row, with 00 / 000) keypad. */
fun keypadHeights(isExpandedKeypad: Boolean): KeypadHeights {
    val rows = rowCount(isExpandedKeypad)
    val padding = KEYPAD_VERTICAL_PADDING * 2
    return KeypadHeights(compact = KEY_ROW_MIN_HEIGHT * rows + padding, full = KEY_ROW_HEIGHT * rows + padding)
}

private fun rowCount(isExpandedKeypad: Boolean): Int = if (isExpandedKeypad) EXPANDED_ROWS else BASIC_ROWS

// Bundled onClick callbacks — one struct so MainActivity can wire them once
// and pass a stable reference down through recompositions.
data class MainKeypadCallbacks(
    val onDigit: (String) -> Unit,
    val onDecimal: () -> Unit,
    val onOperator: (Operator) -> Unit,
    val onPercent: () -> Unit,
    val onOpenParen: () -> Unit,
    val onCloseParen: () -> Unit,
    val onDelete: () -> Unit,
    // C, and a long press on delete: start over.
    val onClear: () -> Unit,
    // =: the calculation's result becomes the amount.
    val onEquals: () -> Unit,
)

/**
 * The calculator keypad: the digits, C, the brackets (one key split in two,
 * each half typing its own), % and delete on three columns of tiles, and the
 * operators on a brass rail beside them with a tall = at its foot. The
 * expanded keypad adds a row of 00 / 0 / 000. Keys that would do nothing
 * now ([keys]: ".", %, ")", =) are dimmed and don't respond. On a wide
 * screen it keeps to one side (Settings' keypadOnLeft), with a button in
 * the space beside it to move it across.
 */
@Composable
fun MainKeypad(
    isExpandedKeypad: Boolean,
    keys: KeyAvailability,
    callbacks: MainKeypadCallbacks,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            // Fills the height it's given, up to full-size rows.
            .heightIn(max = keypadHeights(isExpandedKeypad).full)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = KEYPAD_HORIZONTAL_PADDING, vertical = KEYPAD_VERTICAL_PADDING),
    ) {
        if (maxWidth < ONE_HANDED_MIN_WIDTH) {
            KeypadGrid(isExpandedKeypad, keys, callbacks, Modifier.fillMaxSize())
        } else {
            OneHandedKeypad { grid -> KeypadGrid(isExpandedKeypad, keys, callbacks, grid) }
        }
    }
}

// The keypad at ONE_HANDED_WIDTH on its side of a wide screen, and in the
// space beside it a button, an arrow toward the other side, that moves it
// there.
@Composable
private fun BoxWithConstraintsScope.OneHandedKeypad(grid: @Composable (Modifier) -> Unit) {
    val side = rememberDatabase().display.keypadOnLeft
    val onLeft by side.collectAsStateWithLifecycle()
    val move = stringResource(R.string.a11y_move_keypad)
    grid(
        Modifier
            .width(ONE_HANDED_WIDTH)
            .fillMaxHeight()
            .align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd),
    )
    Box(
        modifier =
            Modifier
                .width(maxWidth - ONE_HANDED_WIDTH)
                .fillMaxHeight()
                .align(if (onLeft) Alignment.CenterEnd else Alignment.CenterStart),
        contentAlignment = Alignment.Center,
    ) {
        FilledTonalIconButton(onClick = rememberHapticOnClick { side.set(!onLeft) }) {
            // Points the way the keypad will go. The keypad is always laid
            // out left to right, so the arrows never mirror here.
            val toward = if (onLeft) R.drawable.ic_arrow_forward else R.drawable.ic_arrow_back
            Icon(painterResource(toward), contentDescription = move)
        }
    }
}

@Composable
private fun KeypadGrid(
    isExpandedKeypad: Boolean,
    keys: KeyAvailability,
    callbacks: MainKeypadCallbacks,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val decimal = remember(context) { getDecimalSeparator(context) }
    Row(modifier) {
        Column(Modifier.weight(DIGIT_COLUMNS).fillMaxHeight()) {
            KeyRow {
                ClearKey(callbacks.onClear)
                BracketsKey(keys.closeParen, callbacks)
                FunctionKey("%", enabled = keys.percent, onClick = callbacks.onPercent)
            }
            DIGIT_ROWS.forEach { digits -> KeyRow { DigitKeys(digits, callbacks) } }
            if (isExpandedKeypad) {
                KeyRow { DigitKeys(EXPANDED_ZERO_ROW, callbacks) }
                KeyRow {
                    DigitKey(decimal, enabled = keys.decimal, onClick = callbacks.onDecimal)
                    DeleteKey(callbacks, span = 2f)
                }
            } else {
                KeyRow {
                    DigitKey(ZERO, callbacks)
                    DigitKey(decimal, enabled = keys.decimal, onClick = callbacks.onDecimal)
                    DeleteKey(callbacks)
                }
            }
        }
        OperatorRail(rowCount(isExpandedKeypad), keys.equals, callbacks, Modifier.weight(1f).fillMaxHeight())
    }
}

// The digit keys, row by row, top to bottom.
private val DIGIT_ROWS = listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"))
private val EXPANDED_ZERO_ROW = listOf(DOUBLE_ZERO, ZERO, TRIPLE_ZERO)
private val RAIL_OPERATORS = listOf(Operator.DIVIDE, Operator.TIMES, Operator.MINUS, Operator.PLUS)

// How each kind of key feels: digits tap lightly, functions and operators a
// little firmer, = confirms, and clearing (C, a long press on delete) is the
// heaviest, as the one that wipes.
private val DIGIT_FEEDBACK = HapticFeedbackType.KeyboardTap
private val FUNCTION_FEEDBACK = HapticFeedbackType.VirtualKey
private val EQUALS_FEEDBACK = HapticFeedbackType.Confirm
private val CLEAR_FEEDBACK = HapticFeedbackType.LongPress

// ÷ × − + on one brass rail, then = two rows tall at its foot, so the rail
// lines up with the rows of tiles beside it. = is dimmed while there's no
// calculation to work out ([canEquals]).
@Composable
private fun OperatorRail(
    rows: Int,
    canEquals: Boolean,
    callbacks: MainKeypadCallbacks,
    modifier: Modifier = Modifier,
) {
    val operatorWeight = (rows - EQUALS_ROWS).toFloat() / OPERATOR_COUNT
    Column(
        modifier =
            modifier
                .padding(KEY_GAP)
                .clip(RAIL_SHAPE)
                .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        RAIL_OPERATORS.forEach { operator ->
            PressableKey(
                onClick = { callbacks.onOperator(operator) },
                feedback = FUNCTION_FEEDBACK,
                modifier = Modifier.weight(operatorWeight).fillMaxWidth(),
            ) {
                KeyLabel(operator.display, MaterialTheme.colorScheme.onSecondaryContainer, OPERATOR_TEXT_SIZE)
            }
        }
        val equals = stringResource(R.string.a11y_equals)
        PressableKey(
            onClick = callbacks.onEquals,
            enabled = canEquals,
            feedback = EQUALS_FEEDBACK,
            modifier =
                Modifier
                    .weight(EQUALS_ROWS.toFloat())
                    .fillMaxWidth()
                    .padding(KEY_GAP * 2)
                    .alpha(if (canEquals) 1f else DISABLED_ROW_ALPHA)
                    .clip(EQUALS_SHAPE)
                    .background(MaterialTheme.colorScheme.primary)
                    .semantics { contentDescription = equals },
        ) {
            KeyLabel("=", MaterialTheme.colorScheme.onPrimary, OPERATOR_TEXT_SIZE)
        }
    }
}

// One row of tiles; the rows share the keypad's height evenly.
@Composable
private fun ColumnScope.KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f),
        content = content,
    )
}

// A key's press: while held it gives a little ([scale]) and its corners
// round ([corner]).
@Stable
private class KeyPress(
    val interaction: MutableInteractionSource,
    pressed: State<Boolean>,
) {
    val held by pressed
}

@Composable
private fun rememberKeyPress(): KeyPress {
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    return remember(interaction) { KeyPress(interaction, pressed) }
}

@Composable
private fun KeyPress.scale(): Float = animateFloatAsState(if (held) PRESSED_SCALE else 1f, Motion.snappy(), label = "keyScale").value

@Composable
private fun KeyPress.corner(): Dp = animateDpAsState(if (held) PRESSED_CORNER else KEY_CORNER, Motion.snappy(), label = "keyCorner").value

// A tile key [span] columns wide in [container]: tap (and long-press) with
// [feedback], and it gives and rounds while pressed; dimmed and inert while
// not [enabled].
@Composable
private fun RowScope.TileKey(
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    span: Float = 1f,
    enabled: Boolean = true,
    feedback: HapticFeedbackType = DIGIT_FEEDBACK,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val press = rememberKeyPress()
    val scale = press.scale()
    Box(
        modifier =
            modifier
                .weight(span)
                .fillMaxHeight()
                .padding(KEY_GAP)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(RoundedCornerShape(press.corner()))
                .background(container)
                .hapticCombinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    enabled = enabled,
                    interactionSource = press.interaction,
                    feedback = feedback,
                    longFeedback = CLEAR_FEEDBACK,
                ).alpha(if (enabled) 1f else DISABLED_ROW_ALPHA),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// A key inside a shared surface (the rail, the split bracket tile): it gives
// a little while pressed.
@Composable
private fun PressableKey(
    onClick: () -> Unit,
    feedback: HapticFeedbackType,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val press = rememberKeyPress()
    val scale = press.scale()
    Box(
        modifier =
            modifier.hapticCombinedClickable(
                onClick = onClick,
                enabled = enabled,
                interactionSource = press.interaction,
                feedback = feedback,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun KeyLabel(
    text: String,
    color: Color,
    size: TextUnit = KEY_TEXT_SIZE,
) {
    Text(text = text, color = color, fontSize = size, fontWeight = FontWeight.Medium)
}

// A key that types [digit].
@Composable
private fun RowScope.DigitKey(
    digit: String,
    callbacks: MainKeypadCallbacks,
) = DigitKey(digit) { callbacks.onDigit(digit) }

@Composable
private fun RowScope.DigitKeys(
    digits: List<String>,
    callbacks: MainKeypadCallbacks,
) = digits.forEach { DigitKey(it, callbacks) }

@Composable
private fun RowScope.DigitKey(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TileKey(
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(UiTestTags.key(label)),
    ) {
        KeyLabel(label, MaterialTheme.colorScheme.onSurface)
    }
}

// %: a function, in brass on the quieter tile.
@Composable
private fun RowScope.FunctionKey(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TileKey(MaterialTheme.colorScheme.surfaceContainerHighest, onClick, enabled = enabled, feedback = FUNCTION_FEEDBACK) {
        KeyLabel(label, MaterialTheme.colorScheme.secondary)
    }
}

// C: start over, on the error tone so it reads as the one that wipes.
@Composable
private fun RowScope.ClearKey(onClick: () -> Unit) {
    val clear = stringResource(R.string.a11y_clear)
    TileKey(
        container = MaterialTheme.colorScheme.errorContainer,
        onClick = onClick,
        feedback = CLEAR_FEEDBACK,
        modifier = Modifier.semantics { contentDescription = clear },
    ) {
        KeyLabel("C", MaterialTheme.colorScheme.onErrorContainer)
    }
}

// "( | )": one tile, two keys, each half typing its own bracket. ")" is
// dimmed and inert while there's nothing it could close ([canClose]).
@Composable
private fun RowScope.BracketsKey(
    canClose: Boolean,
    callbacks: MainKeypadCallbacks,
) {
    val active = MaterialTheme.colorScheme.secondary
    val open = stringResource(R.string.a11y_open_bracket)
    val close = stringResource(R.string.a11y_close_bracket)
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(KEY_GAP)
                .clip(RoundedCornerShape(KEY_CORNER))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            PressableKey(
                onClick = callbacks.onOpenParen,
                feedback = FUNCTION_FEEDBACK,
                modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = open },
            ) {
                KeyLabel(PAREN_OPEN, active)
            }
            Box(
                Modifier
                    .width(SPLIT_DIVIDER_WIDTH)
                    .fillMaxHeight(SPLIT_DIVIDER_HEIGHT)
                    .background(MaterialTheme.colorScheme.background),
            )
            PressableKey(
                onClick = callbacks.onCloseParen,
                feedback = FUNCTION_FEEDBACK,
                enabled = canClose,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .alpha(if (canClose) 1f else DISABLED_ROW_ALPHA)
                        .semantics { contentDescription = close },
            ) {
                KeyLabel(PAREN_CLOSE, active)
            }
        }
    }
}

// Delete; a long press clears, as C does.
@Composable
private fun RowScope.DeleteKey(
    callbacks: MainKeypadCallbacks,
    span: Float = 1f,
) {
    TileKey(
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        onClick = callbacks.onDelete,
        onLongClick = callbacks.onClear,
        span = span,
        feedback = FUNCTION_FEEDBACK,
        modifier = Modifier.testTag(UiTestTags.KEY_DELETE),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_backspace),
            contentDescription = stringResource(id = R.string.a11y_delete),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(DELETE_ICON_SIZE),
        )
    }
}
