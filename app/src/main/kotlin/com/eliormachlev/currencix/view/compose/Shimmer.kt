package com.eliormachlev.currencix.view.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.eliormachlev.currencix.view.compose.theme.Brass

// Full sweep period. 1200ms reads as a steady "working" pulse without
// feeling frantic on a hero digit slot — long enough to be scannable,
// short enough that a two-second rate refresh still shows the motion.
private const val SHIMMER_PERIOD_MILLIS = 1200

// Peak brass alpha at the sweep's crest. 0.20 keeps the digit glyphs
// dominant — the shimmer is a highlight riding over the number, not
// a color-wash that competes with it. Any higher and the digits start
// reading as tinted-brass instead of on-surface ink.
private const val SHIMMER_PEAK_ALPHA = 0.20f

// Sweep width as a fraction of the drawn width. A narrow band (35%)
// reads as a discrete highlight travelling across the digits rather
// than a soft ambient tint.
private const val SHIMMER_BAND_FRACTION = 0.35f

// Sweep travels from -band → 1+band so the band fully enters and
// fully exits the visible width, regardless of band width.
private const val SHIMMER_START_OFFSET = -SHIMMER_BAND_FRACTION
private const val SHIMMER_END_OFFSET = 1f + SHIMMER_BAND_FRACTION

/**
 * Hand-rolled shimmer overlay for the hero digit slot while a rates refresh
 * is in-flight. Paints a brass-tinted linear gradient sweeping left→right
 * over the child's drawn pixels using [BlendMode.SrcAtop], so it never
 * bleeds outside the glyphs. When [enabled] is false this is a no-op — the
 * chain returns [this] unmodified so we don't allocate an animation or
 * a graphics layer on the idle path.
 *
 * [startDelayMillis] lets multiple hero rows stagger their phase so a
 * stack of digit slots doesn't tile identically. Callers that want a
 * single visually-unified sweep across sibling slots should pass the same
 * delay (or share the animation upstream).
 */
@Composable
fun Modifier.shimmer(
    enabled: Boolean,
    startDelayMillis: Int = 0,
): Modifier =
    if (!enabled) {
        this
    } else {
        composed {
            val transition = rememberInfiniteTransition(label = "shimmer")
            val progress by transition.animateFloat(
                initialValue = SHIMMER_START_OFFSET,
                targetValue = SHIMMER_END_OFFSET,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(
                                durationMillis = SHIMMER_PERIOD_MILLIS,
                                delayMillis = startDelayMillis,
                                easing = LinearEasing,
                            ),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "shimmerProgress",
            )
            // `Offscreen` forces the child + shimmer composite into an
            // isolated layer so BlendMode.SrcAtop clips against the child's
            // painted pixels alone — without it, SrcAtop would try to blend
            // against whatever the parent has already drawn underneath.
            this
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val bandPx = size.width * SHIMMER_BAND_FRACTION
                    val leadingEdge = size.width * progress
                    val brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Brass.copy(alpha = SHIMMER_PEAK_ALPHA),
                                    Color.Transparent,
                                ),
                            start = Offset(leadingEdge, 0f),
                            end = Offset(leadingEdge + bandPx, size.height),
                        )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
                    }
                }
        }
    }
