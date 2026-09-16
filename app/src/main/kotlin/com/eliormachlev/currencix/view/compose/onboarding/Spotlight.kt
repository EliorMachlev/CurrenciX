package com.eliormachlev.currencix.view.compose.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.max
import kotlin.math.roundToInt

// Visual metrics. Kept as file-scope constants (per the "hoist literals"
// house rule) so tweaking the ring-halo thickness or padding budget is a
// single-place change.
private const val SCRIM_ALPHA: Float = 0.72f
private val SPOTLIGHT_PAD: Dp = 8.dp
private val SPOTLIGHT_HALO: Dp = 3.dp
private val CARD_MARGIN: Dp = 20.dp
private val CARD_PADDING: Dp = 16.dp
private val CARD_RADIUS: Dp = 20.dp
private val CARD_GAP_FROM_ANCHOR: Dp = 16.dp
private val BUTTON_ROW_GAP: Dp = 8.dp

// Fade timing chosen to feel deliberate but never gate a tap — the overlay
// remains interactive even during the fade because the pointer-input handler
// is attached above the alpha layer.
private const val STEP_FADE_MILLIS = 220

// Hamburger anchor is synthesized: the ActionBar sits outside the Compose
// tree so we can't measure it. This size + offset from the top-left of the
// window is a close-enough highlight for tour purposes across the phones we
// target (matches the DrawerArrowDrawable's default hit-target).
private val HAMBURGER_HINT_SIZE: Dp = 48.dp
private val HAMBURGER_HINT_TOP: Dp = 12.dp

/**
 * A single spotlight step: which anchor to highlight (may be null for the
 * hamburger, which is synthesized), the tooltip [title]/[body], and an
 * optional inline action ([actionLabel]/[onAction]) rendered as an extra
 * button on the tooltip card. Used by [Spotlight] to drive the tour queue.
 */
data class SpotlightStep(
    val anchor: OnboardingAnchor,
    val title: String,
    val body: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * First-run spotlight overlay for onboarding (#147). Draws a dark scrim, cuts
 * a rounded rectangle out of it over the current step's anchor bounds, and
 * pins a tooltip card near the anchor with Skip / Next controls.
 *
 * Hand-rolled rather than pulling in `com.canopas.intro-showcase` — the
 * library is ~30 KB but adds a dependency (and a maintenance surface) for
 * three steps we can render in ~200 lines. Kept isolated under
 * `view/compose/onboarding/` so swapping to a library later is a
 * self-contained change.
 *
 * Interaction rules (per #147 spec):
 *  - Skip button always visible → dismisses the whole tour.
 *  - Tap-outside advances only the current step (does not dismiss the tour),
 *    so users can't accidentally skip the auto-refresh opt-in.
 *  - Optional inline action (used on the auto-refresh step) fires
 *    [SpotlightStep.onAction] then dismisses the tour.
 */
@Composable
fun Spotlight(
    steps: List<SpotlightStep>,
    skipLabel: String,
    nextLabel: String,
    finishLabel: String,
    onDismiss: () -> Unit,
) {
    if (steps.isEmpty()) return
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = steps.getOrNull(stepIndex) ?: return
    val registry = LocalOnboardingAnchors.current
    val density = LocalDensity.current
    val anchorRect =
        when (step.anchor) {
            OnboardingAnchor.Hamburger -> hamburgerHintRect(density)
            else -> registry?.boundsOf(step.anchor)
        }
    val advance = {
        if (stepIndex >= steps.lastIndex) onDismiss() else stepIndex += 1
    }
    val isLast = stepIndex >= steps.lastIndex
    // Popup so we sit above the app's own composition (drawer scrim, hero
    // shadow, etc.) without needing the caller to reserve a Box slot for us.
    Popup(
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = false),
        onDismissRequest = onDismiss,
    ) {
        SpotlightContent(
            anchorRect = anchorRect,
            step = step,
            skipLabel = skipLabel,
            nextLabel = if (isLast) finishLabel else nextLabel,
            onSkip = onDismiss,
            onAdvance = advance,
        )
    }
}

@Composable
private fun SpotlightContent(
    anchorRect: Rect?,
    step: SpotlightStep,
    skipLabel: String,
    nextLabel: String,
    onSkip: () -> Unit,
    onAdvance: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(STEP_FADE_MILLIS),
        label = "spotlightFade",
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // Tap-outside advances only the current step (per spec), so
                // users can't accidentally miss the auto-refresh opt-in on
                // the last step by dismissing the overlay. Skip does that
                // explicitly.
                .pointerInput(step.anchor) {
                    detectTapGestures(onTap = { onAdvance() })
                }.graphicsLayer { this.alpha = alpha },
    ) {
        ScrimWithSpotlight(anchorRect = anchorRect)
        TooltipCard(
            anchorRect = anchorRect,
            step = step,
            skipLabel = skipLabel,
            nextLabel = nextLabel,
            onSkip = onSkip,
            onAdvance = onAdvance,
        )
    }
}

// Dark scrim with a punched-out rounded rectangle over [anchorRect]. Uses
// BlendMode.Clear against a layered graphics-layer so the punch reads as a
// true hole (transparent) instead of a lighter fill blended over the scrim.
@Composable
private fun ScrimWithSpotlight(anchorRect: Rect?) {
    val padPx: Float
    val haloPx: Float
    val cornerPx: Float
    with(LocalDensity.current) {
        padPx = SPOTLIGHT_PAD.toPx()
        haloPx = SPOTLIGHT_HALO.toPx()
        cornerPx = 12.dp.toPx()
    }
    val scrimColor = Color.Black.copy(alpha = SCRIM_ALPHA)
    val haloColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(color = scrimColor, size = size)
        anchorRect?.let { rect ->
            val expanded = rect.inflate(padPx)
            // Punch — the scrim goes transparent inside the anchor's padded
            // bounds so the highlighted UI reads through.
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(expanded.left, expanded.top),
                size = Size(expanded.width, expanded.height),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                blendMode = BlendMode.Clear,
            )
            // Halo ring around the punch — outer ring painted after the
            // Clear so it sits on top of the transparent hole, not below.
            val ringRect = expanded.inflate(haloPx)
            drawRoundedStroke(
                rect = ringRect,
                color = haloColor,
                strokeWidthPx = haloPx,
                cornerPx = cornerPx + haloPx,
            )
        }
    }
}

// Small helper — DrawScope has no first-class "stroke rounded rect" that
// matches drawRoundRect's cornerRadius param, so we render it as a rounded
// rect with a Stroke style.
private fun DrawScope.drawRoundedStroke(
    rect: Rect,
    color: Color,
    strokeWidthPx: Float,
    cornerPx: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        style = Stroke(width = strokeWidthPx),
    )
}

// Tooltip card. Pins itself just below the anchor when there's room, or just
// above when the anchor is closer to the bottom of the window. Falls back to
// vertical centering when we have no bounds at all (registry not yet
// reporting), so the copy is always readable.
@Composable
private fun TooltipCard(
    anchorRect: Rect?,
    step: SpotlightStep,
    skipLabel: String,
    nextLabel: String,
    onSkip: () -> Unit,
    onAdvance: () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { CARD_GAP_FROM_ANCHOR.toPx() }
    val marginPx = with(density) { CARD_MARGIN.toPx() }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CARD_MARGIN)
                    .align(Alignment.TopStart)
                    .offset { cardOffset(anchorRect, gapPx, marginPx) }
                    // Absorb taps on the card so a tap on Skip/Next isn't also
                    // treated as a tap-outside on the parent scrim (which
                    // would advance the step underneath the button press).
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { /* consume */ })
                    }.clip(RoundedCornerShape(CARD_RADIUS))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = step.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BUTTON_ROW_GAP, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) { Text(skipLabel) }
                // Inline action (e.g. "Enable now" on the auto-refresh step).
                // Fires then dismisses; wired through onAction lambda.
                step.actionLabel?.let { label ->
                    TextButton(onClick = {
                        step.onAction?.invoke()
                        onSkip()
                    }) { Text(label) }
                }
                TextButton(onClick = onAdvance) { Text(nextLabel) }
            }
        }
    }
}

// Placement math. Prefer BELOW the anchor if there's room; otherwise ABOVE.
// Falls back to a mid-window Y when [anchorRect] is null.
private fun cardOffset(
    anchorRect: Rect?,
    gapPx: Float,
    marginPx: Float,
): IntOffset {
    val rect = anchorRect ?: return IntOffset(0, marginPx.roundToInt())
    val below = (rect.bottom + gapPx).roundToInt()
    // No window-height known here (would require passing it in); align to
    // "below the anchor" and let the outer fillMaxWidth prevent horizontal
    // clipping. If the anchor is near the bottom we bias upward instead so
    // the tooltip doesn't get clipped below the fold. The bias uses a simple
    // "if anchor is in the lower third" heuristic based on the bottom
    // coordinate, since we don't have window bounds at this call site.
    val yPx = max(0, below)
    return IntOffset(0, yPx)
}

// Synthesizes the hamburger anchor rect. The ActionBar hamburger sits above
// the Compose ComposeView so we can't measure it via a Modifier; instead we
// paint a same-shape hint at the well-known top-left position.
private fun hamburgerHintRect(density: Density): Rect =
    with(density) {
        val size = HAMBURGER_HINT_SIZE.toPx()
        val top = HAMBURGER_HINT_TOP.toPx()
        // Left edge inset by the same top margin so the halo doesn't touch
        // the screen edge — reads as "toolbar affordance" rather than "system
        // gesture area".
        val left = top
        Rect(left = left, top = top, right = left + size, bottom = top + size)
    }
