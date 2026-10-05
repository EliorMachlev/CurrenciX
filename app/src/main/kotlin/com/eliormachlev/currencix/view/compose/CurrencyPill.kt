package com.eliormachlev.currencix.view.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.view.navigation.PillSide
import com.eliormachlev.currencix.view.navigation.sharedCurrencyPillModifier

private val PILL_HEIGHT: Dp = 44.dp
private val PILL_RADIUS: Dp = 999.dp
private val PILL_HORIZONTAL_PADDING: Dp = 14.dp

// Rectangular flag in the picker's 24×17 aspect, scaled to 28×20 so it sits
// comfortably inside the 44dp pill.
private val FLAG_WIDTH: Dp = 28.dp
private val FLAG_HEIGHT: Dp = 20.dp
private val FLAG_CORNER_RADIUS: Dp = 2.dp
private val FLAG_GAP: Dp = 10.dp
private val CHEVRON_SIZE: Dp = 14.dp
private val CHEVRON_GAP: Dp = 4.dp
private val PAIR_ROW_GAP: Dp = 8.dp

/**
 * The flag + ISO code + chevron pill that opens a currency picker. The
 * converter's hero card and the cart footer both use it, and it's the shared
 * element between them: opening the cart flies each pill from the converter
 * into the matching cart pill, and back again on return (see
 * [sharedCurrencyPillModifier] for when two pills count as the same one).
 */
@Composable
fun CurrencyPill(
    currency: Currency?,
    side: PillSide,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flagPainter = currency?.flagPainter()
    Row(
        modifier
            .then(sharedCurrencyPillModifier(side, currency))
            .height(PILL_HEIGHT)
            .clip(RoundedCornerShape(PILL_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .hapticClickable(enabled = currency != null, onClick = onClick)
            .padding(horizontal = PILL_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (flagPainter != null) {
            Image(
                painter = flagPainter,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(width = FLAG_WIDTH, height = FLAG_HEIGHT)
                        .clip(RoundedCornerShape(FLAG_CORNER_RADIUS)),
            )
            Spacer(Modifier.width(FLAG_GAP))
        }
        Text(
            text = currency?.iso4217Alpha().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(CHEVRON_GAP))
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(CHEVRON_SIZE),
        )
    }
}

/** The taps a "from · swap · to" row answers. */
@Immutable
class PairRowActions(
    val onFromClick: () -> Unit,
    val onToClick: () -> Unit,
    val onSwap: () -> Unit,
    val onSwapLongPress: () -> Unit,
)

/**
 * The two currency pills with a swap control between them, as the converter
 * and the cart both show them. Each screen brings its own [swap] button.
 */
@Composable
fun CurrencyPairRow(
    from: Currency?,
    to: Currency?,
    actions: PairRowActions,
    modifier: Modifier = Modifier,
    swap: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PAIR_ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CurrencyPill(
            currency = from,
            side = PillSide.FROM,
            onClick = actions.onFromClick,
            modifier = Modifier.weight(1f).testTag(UiTestTags.PILL_FROM),
        )
        swap()
        CurrencyPill(
            currency = to,
            side = PillSide.TO,
            onClick = actions.onToClick,
            modifier = Modifier.weight(1f).testTag(UiTestTags.PILL_TO),
        )
    }
}
