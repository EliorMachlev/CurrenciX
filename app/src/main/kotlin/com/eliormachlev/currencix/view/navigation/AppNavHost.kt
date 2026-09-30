package com.eliormachlev.currencix.view.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay

/**
 * The window background from the Activity's XML theme — paper, ink, or true
 * black when the OLED option is on. Every screen paints it edge to edge so
 * screens are opaque while they slide over each other.
 */
val LocalScreenBackground = staticCompositionLocalOf { Color.Unspecified }

/** The layout every shared element flies within; null outside the nav host (previews, screenshot tests). */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * The current screen's enter/exit scope. Nullable twin of
 * [LocalNavAnimatedContentScope] (which throws outside a NavDisplay), so
 * components can opt into shared elements and still render on their own.
 */
val LocalScreenVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Hosts every screen of the app. [content] renders one [Screen]; the host
 * adds the back stack handling (including the predictive back gesture), the
 * transitions from ScreenMotion, one ViewModelStore and one saved-state
 * holder per back-stack entry, and the shared-element layout that lets the
 * currency pills fly between the converter and the cart.
 */
@Composable
fun AppNavHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    content: @Composable (Screen) -> Unit,
) {
    SharedTransitionLayout(
        // Lets UiAutomator (the :baselineprofile journeys) find UiTestTags as
        // view resource ids on every screen. Semantics only — nothing visual.
        modifier = modifier.semantics { testTagsAsResourceId = true },
    ) {
        val isTwoPaneWindow = isTwoPaneWindow()
        val twoPane = remember(isTwoPaneWindow) { TwoPaneSceneStrategy<Screen>(isTwoPaneWindow) }
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalTwoPaneWindow provides isTwoPaneWindow,
        ) {
            NavDisplay(
                backStack = navigator.backStack,
                onBack = navigator::pop,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                sceneStrategies = listOf(twoPane, SinglePaneSceneStrategy()),
                sharedTransitionScope = this,
                transitionSpec = pushTransition(),
                popTransitionSpec = popTransition(),
                predictivePopTransitionSpec = predictivePopTransition(),
                entryProvider = { screen ->
                    NavEntry(screen, metadata = paneMetadata(screen.paneRole)) {
                        ScreenFrame(isLeavingTop = { navigator.isLeavingTop(screen) }) { content(screen) }
                    }
                },
            )
        }
    }
}

// True for the screen being popped: gone from the stack already (a
// committed back), or still on top while a predictive back gesture swipes it.
// False for a screen being covered by a new one.
private fun AppNavigator.isLeavingTop(screen: Screen): Boolean = screen !in backStack || backStack.last() == screen

/**
 * One screen's surface: opaque window background, plus the rounded corners
 * the predictive back gesture gives the screen it's swiping away. The radius
 * follows the (seekable) screen transition, and is read only when drawing, so
 * a swipe redraws this layer without recomposing the screen.
 */
@Composable
private fun ScreenFrame(
    isLeavingTop: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val visibilityScope = LocalNavAnimatedContentScope.current
    val rounds: (EnterExitState) -> Boolean = { state -> state == EnterExitState.PostExit && isLeavingTop() }
    val corner by visibilityScope.transition.animateDp(
        // Snap when there's nothing to round: a tween from 0 to 0 would still
        // run its full length and hold every screen transition open for it.
        transitionSpec = { if (rounds(targetState)) ScreenMotion.gesture() else snap() },
        label = "screenCorner",
    ) { state ->
        if (rounds(state)) ScreenMotion.PREDICTIVE_CORNER else 0.dp
    }
    CompositionLocalProvider(LocalScreenVisibilityScope provides visibilityScope) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val radius = corner.toPx()
                    if (radius > 0f) {
                        shape = RoundedCornerShape(radius)
                        clip = true
                    }
                }.background(LocalScreenBackground.current),
        ) { content() }
    }
}
