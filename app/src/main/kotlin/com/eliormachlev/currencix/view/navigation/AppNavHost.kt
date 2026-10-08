package com.eliormachlev.currencix.view.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
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
 * adds the back stack handling, the transitions from ScreenMotion, one
 * ViewModelStore and one saved-state holder per back-stack entry, and the
 * shared-element layout that lets the currency pills fly between the
 * converter and the cart.
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
            val entries =
                rememberDecoratedNavEntries(
                    backStack = navigator.backStack,
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    entryProvider = { screen ->
                        NavEntry(screen, metadata = paneMetadata(screen.paneRole)) {
                            ScreenFrame { content(screen) }
                        }
                    },
                )
            val sceneState =
                rememberSceneState(
                    entries = entries,
                    sceneStrategies = listOf(twoPane, SinglePaneSceneStrategy()),
                    sharedTransitionScope = this,
                    onBack = navigator::pop,
                )
            // Back stays a predictive back callback, so the system knows the
            // app takes it, but the screens don't preview it: the gesture is
            // tracked by the handler's own state, while NavDisplay gets one
            // that never reports a swipe in progress. Letting go then plays
            // the plain pop transition, the same as the up arrow.
            NavigationBackHandler(sceneState, onBackCompleted = navigator::pop)
            NavDisplay(
                sceneState = sceneState,
                navigationEventState = rememberNavigationEventState(sceneState),
                transitionSpec = pushTransition(),
                popTransitionSpec = popTransition(),
            )
        }
    }
}

/** One screen's surface: the opaque window background, edge to edge. */
@Composable
private fun ScreenFrame(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScreenVisibilityScope provides LocalNavAnimatedContentScope.current) {
        Box(
            Modifier
                .fillMaxSize()
                .background(LocalScreenBackground.current),
        ) { content() }
    }
}
