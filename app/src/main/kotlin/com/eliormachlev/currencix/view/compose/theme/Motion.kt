package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * App-wide motion tokens. Every UI transition takes its timing from here, so
 * "make it snappier" is a one-file change and similar gestures move alike.
 *
 * Durations sit on the Material 3 scale: short for small, direct feedback,
 * medium for elements entering or leaving, long for the logo's turn and a
 * chart drawing in.
 * Anything the user can re-trigger mid-flight (taps, drags, panels) uses a
 * spring instead: an interrupted spring keeps its velocity, where a tween
 * restarting from its current value visibly kinks.
 *
 * Content pacing (the loading shimmer's period, the rate pill's auto-scroll)
 * is not a transition and keeps its own constants next to its code.
 */
internal object Motion {
    /** Small, direct feedback — e.g. a value fading to its new text. */
    const val SHORT_MILLIS = 120

    /** Elements entering or leaving — Settings sections, onboarding steps. */
    const val MEDIUM_MILLIS = 180

    /** Gap between successive items of a staggered entrance. */
    const val STAGGER_MILLIS = 20L

    /** A half turn of the logo's arrows (BrandMark): on launch, per refresh. */
    const val TURN_MILLIS = 600

    /** A line drawing itself across a chart. */
    const val DRAW_MILLIS = 600

    // Slight overshoot (~1–2%) — lively on a rotation, invisible on a fade.
    private const val SNAPPY_DAMPING = 0.8f

    // Between Spring.StiffnessMedium (1500) and High: settles about a third
    // sooner than Medium while still reading as a spring, not a snap.
    private const val SPRING_STIFFNESS = 2500f

    /** Short fade for a value swapping in place. */
    fun <T> fadeShort(): TweenSpec<T> = tween(SHORT_MILLIS)

    /** Standard entrance: decelerating, medium length. */
    fun <T> enter(): TweenSpec<T> = tween(MEDIUM_MILLIS, easing = FastOutSlowInEasing)

    /** A chart's line drawing itself in: long, decelerating. */
    fun <T> draw(): TweenSpec<T> = tween(DRAW_MILLIS, easing = FastOutSlowInEasing)

    /** A half turn of the logo's arrows: long, eased at both ends, as on the splash. */
    fun <T> turn(): TweenSpec<T> = tween(TURN_MILLIS, easing = FastOutSlowInEasing)

    /** Tap-driven motion (rotations, toggles): interruptible, a touch of overshoot. */
    fun <T> snappy(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = SNAPPY_DAMPING, stiffness = SPRING_STIFFNESS, visibilityThreshold = visibilityThreshold)

    /** Panels and layout moves: interruptible, no overshoot (nothing may slide past its rest). */
    fun <T> settle(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = SPRING_STIFFNESS, visibilityThreshold = visibilityThreshold)
}
