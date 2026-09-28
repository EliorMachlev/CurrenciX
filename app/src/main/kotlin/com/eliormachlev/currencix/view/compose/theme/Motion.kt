package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
 * medium for elements entering or leaving, long only for one-off flourishes.
 * Anything the user can re-trigger mid-flight (taps, drags, panels) uses a
 * spring instead: an interrupted spring keeps its velocity, where a tween
 * restarting from its current value visibly kinks.
 *
 * Content pacing (the loading shimmer's period, the rate pill's auto-scroll)
 * is not a transition and keeps its own constants next to its code.
 */
internal object Motion {
    /** Small, direct feedback — e.g. a value fading to its new text. */
    const val SHORT_MILLIS = 150

    /** Elements entering or leaving — Settings sections, onboarding steps. */
    const val MEDIUM_MILLIS = 220

    /** Gap between successive items of a staggered entrance. */
    const val STAGGER_MILLIS = 30L

    /** One-off flourish — the launch wordmark. */
    const val LONG_MILLIS = 400

    // Slight overshoot (~1–2%) — lively on a rotation, invisible on a fade.
    private const val SNAPPY_DAMPING = 0.8f

    /** Short fade for a value swapping in place. */
    fun <T> fadeShort(): TweenSpec<T> = tween(SHORT_MILLIS)

    /** Standard entrance: decelerating, medium length. */
    fun <T> enter(): TweenSpec<T> = tween(MEDIUM_MILLIS, easing = FastOutSlowInEasing)

    /** One-off flourish: long, decelerating. */
    fun <T> flourish(): TweenSpec<T> = tween(LONG_MILLIS, easing = LinearOutSlowInEasing)

    /** Tap-driven motion (rotations, toggles): interruptible, a touch of overshoot. */
    fun <T> snappy(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = SNAPPY_DAMPING, stiffness = Spring.StiffnessMedium, visibilityThreshold = visibilityThreshold)

    /** Panels and layout moves: interruptible, no overshoot (nothing may slide past its rest). */
    fun <T> settle(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium, visibilityThreshold = visibilityThreshold)
}
