package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.UiTestTags
import com.eliormachlev.currencix.view.compose.theme.Spacing

private val EMPTY_ICON_CIRCLE = 72.dp
private val EMPTY_ICON_SIZE = 32.dp
private val EMPTY_TOP_SPACE = 48.dp

// A comfortable line length on a wide pane.
private val EMPTY_MAX_WIDTH = 360.dp

// "3.25 + 0.50": the spaces around an operator don't break, so a sum never
// splits across lines.
private val SUM_SPACES = Regex("""(\d) ([+\-−×÷]) (\d)""")
private const val KEEP_TOGETHER = "$1\u00A0$2\u00A0$3"

/**
 * An empty cart: what the cart is for, that a price can be worked out
 * ("3.25 + 0.50"), and a button to add the first item.
 */
@Composable
fun CartEmptyState(
    onAddItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = EMPTY_TOP_SPACE, start = Spacing.XXLarge, end = Spacing.XXLarge)
                .wrapContentWidth()
                .widthIn(max = EMPTY_MAX_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(EMPTY_ICON_CIRCLE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cart),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(EMPTY_ICON_SIZE),
            )
        }
        Text(
            text = stringResource(R.string.cart_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.cart_empty_body).replace(SUM_SPACES, KEEP_TOGETHER),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(
            onClick = rememberHapticOnClick(onAddItem),
            modifier = Modifier.padding(top = Spacing.Small).testTag(UiTestTags.CART_ADD),
        ) {
            AddLabel(stringResource(R.string.cart_add_first_item))
        }
    }
}

/** The row after the last item that adds another. */
@Composable
fun AddItemRow(
    onAddItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = Spacing.Small)
                .clip(MaterialTheme.shapes.medium)
                .hapticClickable(onClick = onAddItem)
                .padding(horizontal = Spacing.Small, vertical = Spacing.Medium),
    ) {
        AddLabel(stringResource(R.string.cart_add_item), color = MaterialTheme.colorScheme.primary)
    }
}

// "+ label", the plus in the label's color.
@Composable
private fun AddLabel(
    text: String,
    color: Color = LocalContentColor.current,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, tint = color)
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}
