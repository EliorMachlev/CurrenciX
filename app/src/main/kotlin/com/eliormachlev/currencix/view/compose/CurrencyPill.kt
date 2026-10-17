package com.eliormachlev.currencix.view.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.hapticCombinedClickable
import com.eliormachlev.currencix.view.compose.theme.Motion
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.navigation.PillSide
import com.eliormachlev.currencix.view.navigation.rememberPillTransition
import com.eliormachlev.currencix.view.navigation.sharedCurrencyPill

private val PILL_HEIGHT: Dp = 44.dp
private val PILL_RADIUS: Dp = 999.dp
private val PILL_HORIZONTAL_PADDING: Dp = Spacing.MediumPlus

// Rectangular flag in the picker's 24×17 aspect, scaled to 28×20 so it sits
// comfortably inside the 44dp pill.
private val FLAG_GAP: Dp = Spacing.SmallPlus
private val CHEVRON_SIZE: Dp = 14.dp
private val CHEVRON_GAP: Dp = Spacing.Tiny
private val PAIR_ROW_GAP: Dp = Spacing.Small
private val SWAP_FAB_SIZE: Dp = 44.dp

/**
 * The room between a [CurrencyPairRow]'s two pills (the swap button and its
 * gaps), for content laid out in columns under them.
 */
internal val PAIR_ROW_MIDDLE: Dp = SWAP_FAB_SIZE + PAIR_ROW_GAP * 2
private val SWAP_GLYPH_SIZE: Dp = 22.dp

// One 180° flip of the swap glyph per tap. The counter drives a target
// angle so successive taps keep spinning the same way (never snap back),
// and a second tap mid-flip carries the spin on instead of restarting it.
private const val SWAP_FAB_ROTATION_STEP = 180f

/**
 * The flag + ISO code + chevron pill that opens a currency picker. The
 * converter's hero card and the cart footer both use it, and it's the shared
 * element between them: opening the cart flies each pill from the converter
 * into the matching cart pill, and back again on return (see
 * [rememberPillTransition] for when two pills count as the same one).
 */
@Composable
fun CurrencyPill(
    currency: Currency?,
    side: PillSide,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // False where the pill shares the screen with the converter's own (a
    // sheet over it): two pills with one key would fight over the flight.
    sharedElement: Boolean = true,
) {
    val transition = rememberPillTransition(side, currency).takeIf { sharedElement }
    Row(
        modifier
            .sharedCurrencyPill(transition)
            .height(PILL_HEIGHT)
            .clip(RoundedCornerShape(PILL_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .hapticClickable(enabled = currency != null, onClick = onClick)
            .padding(horizontal = PILL_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (currency != null) {
            CurrencyFlag(currency, FlagSize.Large)
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
 * The two currency pills with the round swap button between them: the
 * converter, the cart, the rate chart and Quick conversions all show their
 * pair this way, "from" on the left in every language. [swapModifier]
 * decorates the swap button (the converter's onboarding anchors to it);
 * [swapEnabled] false greys it out while a swap can't be done yet.
 * [sharedElement] as on [CurrencyPill].
 */
@Composable
fun CurrencyPairRow(
    from: Currency?,
    to: Currency?,
    actions: PairRowActions,
    modifier: Modifier = Modifier,
    swapModifier: Modifier = Modifier,
    swapEnabled: Boolean = true,
    sharedElement: Boolean = true,
) {
    val trade = rememberPillTrade(from, to)
    var rowWidth by remember { mutableIntStateOf(0) }
    var pillWidth by remember { mutableIntStateOf(0) }
    // How far a pill travels to the other's place: the row less one pill.
    val travel = { (rowWidth - pillWidth) * trade.remaining() }
    // From → to reads left to right in every language, also inside a screen
    // or sheet that follows Hebrew's direction.
    Ltr {
        Row(
            modifier = modifier.fillMaxWidth().onSizeChanged { rowWidth = it.width },
            horizontalArrangement = Arrangement.spacedBy(PAIR_ROW_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CurrencyPill(
                currency = from,
                side = PillSide.FROM,
                onClick = actions.onFromClick,
                modifier =
                    Modifier
                        .weight(1f)
                        .onSizeChanged { pillWidth = it.width }
                        .graphicsLayer { translationX = travel() }
                        .testTag(UiTestTags.PILL_FROM),
                sharedElement = sharedElement,
            )
            SwapFab(onClick = actions.onSwap, onLongClick = actions.onSwapLongPress, modifier = swapModifier, enabled = swapEnabled)
            CurrencyPill(
                currency = to,
                side = PillSide.TO,
                onClick = actions.onToClick,
                modifier = Modifier.weight(1f).graphicsLayer { translationX = -travel() }.testTag(UiTestTags.PILL_TO),
                sharedElement = sharedElement,
            )
        }
    }
}

// The round swap button between the pills: a tap swaps (the glyph turning
// half a turn each time), a long-press does what the screen gives it.
@Composable
private fun SwapFab(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    var tapCount by remember { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(
        targetValue = tapCount * SWAP_FAB_ROTATION_STEP,
        animationSpec = Motion.snappy(),
        label = "swapFabRotation",
    )
    Box(
        modifier
            .size(SWAP_FAB_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else DISABLED_ALPHA))
            .hapticCombinedClickable(
                enabled = enabled,
                onClick = {
                    tapCount += 1
                    onClick()
                },
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_swap_horiz),
            contentDescription = stringResource(R.string.desc_toggle_currencies),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier =
                Modifier
                    .size(SWAP_GLYPH_SIZE)
                    .graphicsLayer { rotationZ = rotation },
        )
    }
}

// Material's opacity for a disabled control.
private const val DISABLED_ALPHA = 0.38f

// The two pills trading places after a swap: each starts where the other
// was and slides home. [remaining] is how much of the way is left, 1 → 0.
private class PillTrade(
    private val progress: Animatable<Float, *>,
    private val pendingStart: () -> Boolean,
) {
    fun remaining(): Float = if (pendingStart()) 1f else progress.value
}

@Composable
private fun rememberPillTrade(
    from: Currency?,
    to: Currency?,
): PillTrade {
    val progress = remember { Animatable(0f) }
    val last = remember { arrayOf(from, to) }
    // Worked out while composing, so the swapped pills are drawn at their
    // old places from the very first frame, not after a frame at the new.
    val swapped =
        remember(from, to) {
            val traded = from != null && to != null && from != to && from == last[1] && to == last[0]
            last[0] = from
            last[1] = to
            traded
        }
    var started by remember(from, to) { mutableStateOf(!swapped) }
    LaunchedEffect(from, to) {
        if (!swapped) return@LaunchedEffect
        progress.snapTo(1f)
        started = true
        progress.animateTo(0f, Motion.settle())
    }
    return remember(progress) { PillTrade(progress) { !started } }
}
