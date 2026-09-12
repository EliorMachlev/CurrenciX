package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.util.getDecimalSeparator
import com.eliormachlev.currencix.util.hapticCombinedClickable
import com.eliormachlev.currencix.viewmodel.main.Operator

// Fixed row height keeps the keypad compact regardless of screen size. The
// display column above absorbs any remaining space via a LinearLayout weight
// so the hero card can grow on tall phones without the keypad also expanding
// (which is exactly what the old ConstraintLayout keypad did, and what pushed
// the tinted "you get" band off-screen when the display had extra content).
private val KEY_ROW_HEIGHT: Dp = 56.dp
private val KEY_TEXT_SIZE = 26.sp
private val KEYPAD_VERTICAL_PADDING: Dp = 4.dp
private val KEYPAD_HORIZONTAL_PADDING: Dp = 4.dp
private val DELETE_ICON_SIZE: Dp = 24.dp

// Bundled onClick callbacks — one struct so MainActivity can wire them once
// and pass a stable reference down through recompositions.
data class MainKeypadCallbacks(
    val onDigit: (String) -> Unit,
    val onDecimal: () -> Unit,
    val onOperator: (Operator) -> Unit,
    val onPercent: () -> Unit,
    val onParens: () -> Unit,
    val onDelete: () -> Unit,
    val onDeleteLong: () -> Unit,
)

@Composable
fun MainKeypad(
    keyboardType: KeyboardType,
    nextParen: Char,
    callbacks: MainKeypadCallbacks,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val decimal = remember(context) { getDecimalSeparator(context) }
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = KEYPAD_HORIZONTAL_PADDING, vertical = KEYPAD_VERTICAL_PADDING),
        verticalArrangement = Arrangement.Center,
    ) {
        if (keyboardType == KeyboardType.EXPANDED) {
            ExpandedKeypadRows(decimal, nextParen, callbacks)
        } else {
            BasicKeypadRows(decimal, callbacks)
        }
    }
}

// BASIC — 4×4 grid, operators stacked as the trailing column with the
// backspace tucked into the last row between "." and "+".
@Composable
private fun BasicKeypadRows(
    decimal: String,
    callbacks: MainKeypadCallbacks,
) {
    KeyRow {
        DigitKey("7") { callbacks.onDigit("7") }
        DigitKey("8") { callbacks.onDigit("8") }
        DigitKey("9") { callbacks.onDigit("9") }
        OperatorKey("÷") { callbacks.onOperator(Operator.DIVIDE) }
    }
    KeyRow {
        DigitKey("4") { callbacks.onDigit("4") }
        DigitKey("5") { callbacks.onDigit("5") }
        DigitKey("6") { callbacks.onDigit("6") }
        OperatorKey("×") { callbacks.onOperator(Operator.TIMES) }
    }
    KeyRow {
        DigitKey("1") { callbacks.onDigit("1") }
        DigitKey("2") { callbacks.onDigit("2") }
        DigitKey("3") { callbacks.onDigit("3") }
        OperatorKey("−") { callbacks.onOperator(Operator.MINUS) }
    }
    KeyRow {
        DigitKey("0") { callbacks.onDigit("0") }
        DigitKey(decimal, onClick = callbacks.onDecimal)
        DeleteKey(onClick = callbacks.onDelete, onLongClick = callbacks.onDeleteLong)
        OperatorKey("+") { callbacks.onOperator(Operator.PLUS) }
    }
}

// EXPANDED — adds a leading operators row and moves the digit-zero cluster
// (00 / 0 / 000) to a dedicated bottom row next to backspace, matching the
// v1 xml keypad but with fixed compact row heights.
@Composable
private fun ExpandedKeypadRows(
    decimal: String,
    nextParen: Char,
    callbacks: MainKeypadCallbacks,
) {
    KeyRow {
        OperatorKey("÷") { callbacks.onOperator(Operator.DIVIDE) }
        OperatorKey("×") { callbacks.onOperator(Operator.TIMES) }
        OperatorKey("−") { callbacks.onOperator(Operator.MINUS) }
        OperatorKey("+") { callbacks.onOperator(Operator.PLUS) }
    }
    KeyRow {
        DigitKey("7") { callbacks.onDigit("7") }
        DigitKey("8") { callbacks.onDigit("8") }
        DigitKey("9") { callbacks.onDigit("9") }
        OperatorKey("%", onClick = callbacks.onPercent)
    }
    KeyRow {
        DigitKey("4") { callbacks.onDigit("4") }
        DigitKey("5") { callbacks.onDigit("5") }
        DigitKey("6") { callbacks.onDigit("6") }
        ParensKey(nextParen = nextParen, onClick = callbacks.onParens)
    }
    KeyRow {
        DigitKey("1") { callbacks.onDigit("1") }
        DigitKey("2") { callbacks.onDigit("2") }
        DigitKey("3") { callbacks.onDigit("3") }
        DigitKey(decimal, onClick = callbacks.onDecimal)
    }
    KeyRow {
        DigitKey("00") { callbacks.onDigit("00") }
        DigitKey("0") { callbacks.onDigit("0") }
        DigitKey("000") { callbacks.onDigit("000") }
        DeleteKey(onClick = callbacks.onDelete, onLongClick = callbacks.onDeleteLong)
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(KEY_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// KeyCell — one cell of the grid. Uses Modifier.weight so all four cells in a
// row share the row width evenly, and takes the shared hapticCombinedClickable
// so tap / long-tap both fire the standard keyboard-tap haptic.
@Composable
private fun RowScope.KeyCell(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxSize()
                .hapticCombinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun RowScope.DigitKey(
    label: String,
    onClick: () -> Unit,
) {
    KeyCell(onClick = onClick) {
        Text(
            text = label,
            fontSize = KEY_TEXT_SIZE,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RowScope.OperatorKey(
    label: String,
    onClick: () -> Unit,
) {
    KeyCell(onClick = onClick) {
        Text(
            text = label,
            fontSize = KEY_TEXT_SIZE,
            color = colorResource(id = R.color.color_keypad_operators),
        )
    }
}

// ParensKey — highlights whichever glyph will be inserted on the next tap
// (bold + operator green) and dims the other, matching the ParenButton
// util the xml button used to render.
@Composable
private fun RowScope.ParensKey(
    nextParen: Char,
    onClick: () -> Unit,
) {
    val activeColor = colorResource(id = R.color.color_keypad_operators)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val label =
        remember(nextParen, activeColor, mutedColor) {
            buildParenLabel(nextParen, activeColor, mutedColor)
        }
    KeyCell(onClick = onClick) {
        Text(text = label, fontSize = KEY_TEXT_SIZE)
    }
}

private fun buildParenLabel(
    nextParen: Char,
    activeColor: Color,
    mutedColor: Color,
) = buildAnnotatedString {
    listOf('(', ')').forEach { ch ->
        val active = nextParen == ch
        withStyle(
            SpanStyle(
                color = if (active) activeColor else mutedColor,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            ),
        ) { append(ch.toString()) }
    }
}

@Composable
private fun RowScope.DeleteKey(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    KeyCell(onClick = onClick, onLongClick = onLongClick) {
        Icon(
            painter = painterResource(id = R.drawable.ic_backspace),
            contentDescription = stringResource(id = R.string.a11y_delete),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(DELETE_ICON_SIZE),
        )
    }
}
