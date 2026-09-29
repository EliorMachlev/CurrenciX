package com.eliormachlev.currencix.view.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

/**
 * Screen-to-screen motion. The same shape the Activity transitions had
 * (res/anim/screen_* before the single-Activity move): the new screen slides
 * in a short way from the right and fades in over the old one, and leaving
 * reverses it. What's new is that the screen underneath now takes part — it
 * sinks back a little instead of freezing — and that the predictive back
 * gesture scrubs a dedicated transition frame by frame.
 */
internal object ScreenMotion {
    /** Whole-screen moves: between Motion.MEDIUM and Motion.LONG, a screen travels further than an element. */
    const val TRANSITION_MILLIS = 250

    /** The incoming screen is legible before its slide settles. */
    const val FADE_IN_MILLIS = 180

    /** Horizontal travel as a fraction of the width: 1/10, like the old 10% XML translate. */
    const val SLIDE_DIVISOR = 10

    /** How far the screen underneath sinks while another one covers it. */
    const val DEPTH_SCALE = 0.97f

    /**
     * The predictive back transition is seeked by the gesture: its timeline
     * maps onto the swipe (0 → 1), and letting go plays the remainder at
     * this pace.
     */
    const val PREDICTIVE_MILLIS = 300

    /** Material's predictive back shrink for the screen being swiped away. */
    const val PREDICTIVE_SCALE = 0.9f

    /** It also drifts toward the side the swipe started from… */
    const val PREDICTIVE_SLIDE_DIVISOR = 20

    /** …stays fully opaque for most of the swipe, and only fades at the very end. */
    const val PREDICTIVE_FADE_START_FRACTION = 0.7f

    /** Corner radius the swiped-away screen rounds to (ScreenFrame). */
    val PREDICTIVE_CORNER: Dp = 28.dp

    fun <T> decelerate(millis: Int = TRANSITION_MILLIS): TweenSpec<T> = tween(millis, easing = FastOutSlowInEasing)

    fun <T> accelerate(millis: Int = TRANSITION_MILLIS): TweenSpec<T> = tween(millis, easing = FastOutLinearInEasing)

    // Linear, so the swiped screen tracks the finger 1:1.
    fun <T> gesture(): TweenSpec<T> = tween(PREDICTIVE_MILLIS, easing = LinearEasing)
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

/** Back via the up arrow or a plain back press: the reverse of [pushTransition]. */
internal fun <T : Any> popTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
    {
        ContentTransform(
            targetContentEnter = scaleIn(ScreenMotion.decelerate(), initialScale = ScreenMotion.DEPTH_SCALE),
            initialContentExit =
                slideOutHorizontally(ScreenMotion.accelerate()) { width -> width / ScreenMotion.SLIDE_DIVISOR } +
                    fadeOut(ScreenMotion.accelerate()),
        )
    }

/**
 * The predictive back gesture: the current screen shrinks and drifts with the
 * finger (ScreenFrame rounds its corners) while the previous one rises
 * back into place beneath it. Releasing past the commit point finishes the
 * timeline, which is when the swiped screen fades; cancelling rewinds it.
 */
internal fun <T : Any> predictivePopTransition(): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform =
    { swipeEdge ->
        // Swiping from the right edge pushes the screen left, and vice versa.
        val direction = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
        ContentTransform(
            targetContentEnter = scaleIn(ScreenMotion.gesture(), initialScale = ScreenMotion.DEPTH_SCALE),
            initialContentExit =
                scaleOut(ScreenMotion.gesture(), targetScale = ScreenMotion.PREDICTIVE_SCALE) +
                    slideOutHorizontally(ScreenMotion.gesture()) { width -> direction * width / ScreenMotion.PREDICTIVE_SLIDE_DIVISOR } +
                    fadeOut(
                        keyframes {
                            durationMillis = ScreenMotion.PREDICTIVE_MILLIS
                            1f at (ScreenMotion.PREDICTIVE_MILLIS * ScreenMotion.PREDICTIVE_FADE_START_FRACTION).toInt()
                        },
                    ),
        )
    }
