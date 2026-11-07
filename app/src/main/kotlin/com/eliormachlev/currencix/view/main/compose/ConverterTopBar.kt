package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.TopBarAction
import com.eliormachlev.currencix.view.compose.onboarding.OnboardingAnchor
import com.eliormachlev.currencix.view.compose.onboarding.onboardingAnchor
import com.eliormachlev.currencix.view.compose.screenTopBarColors
import com.eliormachlev.currencix.view.compose.theme.Wordmark
import com.eliormachlev.currencix.view.compose.theme.WordmarkReveal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val WORDMARK_TITLE_SIZE = 26.sp
private val DRAWER_ARROW_SIZE = 24.dp

// The morphing hamburger, drawn to match the Material Symbols Rounded "menu"
// the rest of the icons come from: 1.5 dp bars with round caps, 18 dp wide
// end to end, with 3.75 dp of space between bars.
private val DRAWER_BAR_THICKNESS = 1.5.dp
private val DRAWER_BAR_LENGTH = 18.dp
private val DRAWER_BAR_GAP = 3.75.dp

// The arrow's head and shaft, and the angle its head's two bars make with the shaft.
private val DRAWER_ARROW_HEAD_LENGTH = 8.dp
private val DRAWER_ARROW_SHAFT_LENGTH = 16.dp
private const val ARROW_HEAD_ANGLE = PI / 4
private const val HALF_TURN = 180f

// The bars' stack is placed on a grid of this many pixels, and the middle
// bar's centre is this many bar thicknesses below the top of the stack.
private const val BAR_COUNT = 3
private const val PIXEL_GRID = 4
private const val MIDDLE_BAR_CENTRE = 1.5f

/** The converter bar's shortcut actions — each also lives in the drawer. */
@Immutable
data class ConverterTopBarActions(
    val onTimeline: () -> Unit,
    val onCart: () -> Unit,
    val onQuickConversions: () -> Unit,
    val onHistoricalRates: () -> Unit,
    val onRefresh: () -> Unit,
)

/**
 * The converter's top bar: drawer button, the Currenci× wordmark, and the
 * four highest-frequency shortcuts as always-visible icons (everything else
 * lives in the drawer). [reveal] is the wordmark's cold-start reveal and
 * its hand-off with the splash screen; its logo turns while [isRefreshing],
 * and tapping it refreshes the rates.
 */
@Composable
fun ConverterTopBar(
    drawerState: DrawerState,
    onToggleDrawer: () -> Unit,
    actions: ConverterTopBarActions,
    reveal: WordmarkReveal,
    isRefreshing: Boolean,
) {
    TopAppBar(
        title = {
            Wordmark(
                fontSize = WORDMARK_TITLE_SIZE,
                reveal = reveal,
                turning = isRefreshing,
                onClick = actions.onRefresh,
            )
        },
        navigationIcon = {
            val description =
                stringResource(if (drawerState.targetValue == DrawerValue.Open) R.string.desc_close_drawer else R.string.desc_open_drawer)
            IconButton(
                onClick = rememberHapticOnClick(onToggleDrawer),
                modifier =
                    Modifier
                        .onboardingAnchor(OnboardingAnchor.Hamburger)
                        .semantics { contentDescription = description },
            ) {
                DrawerArrowIcon(drawerState)
            }
        },
        actions = {
            TopBarAction(painterResource(R.drawable.ic_timeline), stringResource(R.string.menu_timeline), actions.onTimeline)
            TopBarAction(painterResource(R.drawable.ic_cart), stringResource(R.string.cart_title), actions.onCart)
            TopBarAction(
                painterResource(R.drawable.ic_table),
                stringResource(R.string.menu_quick_conversions),
                actions.onQuickConversions,
            )
            TopBarAction(
                painterResource(R.drawable.ic_history),
                stringResource(R.string.menu_historical_rates),
                actions.onHistoricalRates,
            )
        },
        colors = screenTopBarColors(),
    )
}

/**
 * The hamburger ↔ arrow morph, driven by the drawer's actual position: it
 * tracks a finger dragging the drawer and a fling's real velocity instead of
 * playing its own timed animation. The offset is collected into a float
 * state that only the Canvas's draw block reads, so a drag redraws this icon
 * and recomposes nothing.
 *
 * The sheet sits at -(its width) when closed and 0 when open, in either
 * direction (the drawer's offset follows the layout direction). The arrow
 * points the way the drawer closes: left in English, right in Hebrew.
 * Rather than hard-code the sheet width, the closed offset is
 * learned as the smallest one seen: the offset never goes below it, and the
 * drawer starts closed, so the first value already is it.
 */
@Composable
private fun DrawerArrowIcon(drawerState: DrawerState) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(drawerState) {
        var closedOffset = Float.NaN
        snapshotFlow { drawerState.currentOffset }
            .collect { offset ->
                if (offset.isNaN()) return@collect // anchors not measured yet
                closedOffset = if (closedOffset.isNaN()) offset else minOf(closedOffset, offset)
                progress.floatValue =
                    if (closedOffset < 0f) {
                        (1f - offset / closedOffset).coerceIn(0f, 1f)
                    } else {
                        // Restored straight into the open state: no closed
                        // offset seen yet, so fall back to the settled value.
                        if (drawerState.currentValue == DrawerValue.Open) 1f else 0f
                    }
            }
    }
    val tint = LocalContentColor.current
    Canvas(Modifier.size(DRAWER_ARROW_SIZE)) {
        drawDrawerArrow(progress.floatValue, isRtl, tint)
    }
}

/**
 * The hamburger at [progress] 0 and the arrow at 1: three round-capped bars
 * that shorten, tilt and turn half a circle into an arrow pointing left (right
 * when [isRtl]). The same geometry as AppCompat's DrawerArrowDrawable, which
 * this replaces: the middle bar keeps its middle, the other two meet it at 45°.
 */
private fun DrawScope.drawDrawerArrow(
    progress: Float,
    isRtl: Boolean,
    color: Color,
) {
    val thickness = DRAWER_BAR_THICKNESS.toPx()
    // Round caps reach half a bar past each end.
    val barLength = (DRAWER_BAR_LENGTH - DRAWER_BAR_THICKNESS).toPx()
    val gap = DRAWER_BAR_GAP.toPx()
    val maxCut = (thickness / 2 * cos(ARROW_HEAD_ANGLE)).toFloat()
    val arrowSize = lerp(barLength, DRAWER_ARROW_HEAD_LENGTH.toPx(), progress)
    val middleBarCut = lerp(barLength, DRAWER_ARROW_SHAFT_LENGTH.toPx(), progress)
    val cut = lerp(0f, maxCut, progress).roundToInt().toFloat()
    // The outer bars are level in the hamburger and tilt to the arrow-head angle.
    val angle = lerp(0f, ARROW_HEAD_ANGLE.toFloat(), progress)
    val arrowWidth = (arrowSize * cos(angle)).roundToInt().toFloat()
    val arrowHeight = (arrowSize * sin(angle)).roundToInt().toFloat()
    val topBottomOffset = lerp(gap + thickness, -maxCut, progress)
    val arrowEdge = -middleBarCut / 2
    val spin = lerp(if (isRtl) 0f else -HALF_TURN, if (isRtl) HALF_TURN else 0f, progress) * if (isRtl) -1f else 1f

    val bars =
        Path().apply {
            moveTo(arrowEdge + cut, 0f)
            relativeLineTo(middleBarCut - cut * 2, 0f)
            moveTo(arrowEdge, topBottomOffset)
            relativeLineTo(arrowWidth, arrowHeight)
            moveTo(arrowEdge, -topBottomOffset)
            relativeLineTo(arrowWidth, -arrowHeight)
        }
    // The bars are drawn around their own middle, which sits a whole number of
    // pixels below the top of the box so the lines stay crisp.
    val remaining = (size.height - thickness * BAR_COUNT - gap * 2).toInt()
    val middleY = (remaining / PIXEL_GRID) * 2f + thickness * MIDDLE_BAR_CENTRE + gap
    translate(left = size.width / 2, top = middleY) {
        rotate(degrees = spin, pivot = Offset.Zero) {
            drawPath(bars, color, style = Stroke(width = thickness, cap = StrokeCap.Round, join = StrokeJoin.Miter))
        }
    }
}
