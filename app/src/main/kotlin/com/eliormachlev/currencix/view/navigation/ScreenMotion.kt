package com.eliormachlev.currencix.view.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation3.scene.Scene

/**
 * Screen-to-screen motion. The same shape the Activity transitions had
 * (res/anim/screen_* before the single-Activity move): the new screen slides
 * in a short way from the right and fades in over the old one, and leaving
 * reverses it. What's new is that the screen underneath now takes part — it
 * sinks back a little instead of freezing. A back gesture doesn't preview
 * the pop: the screen stays put under the finger, and letting go plays
 * [popTransition], like any other back.
 */
internal object ScreenMotion {
    /** Whole-screen moves: a little longer than Motion.MEDIUM, since a screen travels further than an element. */
    const val TRANSITION_MILLIS = 200

    /** The incoming screen is legible before its slide settles. */
    const val FADE_IN_MILLIS = 120

    /** Horizontal travel as a fraction of the width: 1/10, like the old 10% XML translate. */
    const val SLIDE_DIVISOR = 10

    /** How far the screen underneath sinks while another one covers it. */
    const val DEPTH_SCALE = 0.97f

    fun <T> decelerate(millis: Int = TRANSITION_MILLIS): TweenSpec<T> = tween(millis, easing = FastOutSlowInEasing)

    fun <T> accelerate(millis: Int = TRANSITION_MILLIS): TweenSpec<T> = tween(millis, easing = FastOutLinearInEasing)
}

/** Opening a screen: it slides in over the current one, which sinks back. */
internal fun <T : Any> pushTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
    {
        ContentTransform(
            targetContentEnter =
                slideInHorizontally(ScreenMotion.decelerate()) { width -> width / ScreenMotion.SLIDE_DIVISOR } +
                    fadeIn(tween(ScreenMotion.FADE_IN_MILLIS, easing = LinearOutSlowInEasing)),
            // Also keeps the old screen composed until the new one has fully
            // covered it, so the window background never shows through.
            initialContentExit = scaleOut(ScreenMotion.decelerate(), targetScale = ScreenMotion.DEPTH_SCALE),
        )
    }

/** Back, by the up arrow, the button or the gesture: the reverse of [pushTransition]. */
internal fun <T : Any> popTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
    {
        ContentTransform(
            targetContentEnter = scaleIn(ScreenMotion.decelerate(), initialScale = ScreenMotion.DEPTH_SCALE),
            initialContentExit =
                slideOutHorizontally(ScreenMotion.accelerate()) { width -> width / ScreenMotion.SLIDE_DIVISOR } +
                    fadeOut(ScreenMotion.accelerate()),
        )
    }
