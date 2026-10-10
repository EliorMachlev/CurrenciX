package com.eliormachlev.currencix.view.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.hapticCombinedClickable
import com.eliormachlev.currencix.view.compose.dialogs.LedgerConfirmSheet

private val CHIP_HEIGHT: Dp = 36.dp
private val CHIP_PADDING_H: Dp = 12.dp
private val CHIP_INNER_GAP: Dp = 6.dp
private val FLAG_WIDTH: Dp = 20.dp
private val FLAG_HEIGHT: Dp = 14.dp
private val FLAG_CORNER: Dp = 2.dp
private val PAIR_ARROW_SIZE: Dp = 14.dp

/** Space between neighbouring [CurrencyChip]s in a row. */
val CurrencyChipGap: Dp = 8.dp

/**
 * A pill-shaped shortcut to a currency or pair — the converter's recent
 * pairs and the picker's recent currencies. [description] is what TalkBack
 * reads for the whole chip; put [FlagCode]s (and any glyphs) in [content].
 * [onLongClick], when given, is announced as [onLongClickLabel].
 */
@Composable
fun CurrencyChip(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    ChipFrame(
        modifier = modifier,
        // Inside the pill's clip, so the ripple keeps its shape.
        action =
            Modifier
                .hapticCombinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel)
                .semantics(mergeDescendants = true) { contentDescription = description },
        content = content,
    )
}

// The pill itself: height, shape, tone and inner spacing; [action] (a click)
// goes inside the clip.
@Composable
private fun ChipFrame(
    modifier: Modifier = Modifier,
    action: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .height(CHIP_HEIGHT)
            .clip(RoundedCornerShape(CHIP_HEIGHT / 2))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(action)
            .padding(horizontal = CHIP_PADDING_H),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CHIP_INNER_GAP),
        content = content,
    )
}

/** A recent pair's chip content: from, an arrow, to. */
@Composable
fun PairChipContent(
    from: Currency,
    to: Currency,
) {
    FlagCode(from)
    Icon(
        painter = painterResource(R.drawable.ic_arrow_forward),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(PAIR_ARROW_SIZE),
    )
    FlagCode(to)
}

/**
 * Asks before a recent pair or currency is forgotten — what a long-press on
 * its [CurrencyChip] opens. A sheet, like the app's other prompts: the chip
 * being removed ([chip], flags and all), what goes ([message]), then Cancel
 * and Delete.
 */
@Composable
fun RemoveFromHistorySheet(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    chip: @Composable RowScope.() -> Unit,
) {
    LedgerConfirmSheet(
        title = stringResource(R.string.recent_remove_title),
        message = message,
        confirmLabel = stringResource(R.string.a11y_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
        // The chip reads left to right in every language, as in its row.
        header = { Ltr { ChipFrame(content = chip) } },
    )
}

/** A small flag and the ISO code — one side of a [CurrencyChip]. */
@Composable
fun FlagCode(currency: Currency) {
    Image(
        painter = currency.flagPainter(),
        contentDescription = null,
        modifier =
            Modifier
                .size(width = FLAG_WIDTH, height = FLAG_HEIGHT)
                .clip(RoundedCornerShape(FLAG_CORNER)),
    )
    Text(
        text = currency.iso4217Alpha(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
