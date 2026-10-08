package com.eliormachlev.currencix.view.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * The app's back stack. The converter is always at the bottom; every other
 * screen is pushed on top of it and popped off by back (button or gesture)
 * or by the screen itself.
 *
 * Saved with the Activity's instance state, so a rotation, a theme change
 * (which recreates the Activity) or process death comes back to the same
 * screen with the same history.
 */
@Stable
class AppNavigator internal constructor(
    initial: List<Screen>,
) {
    /** Read by NavDisplay; mutate only through [navigate] / [pop]. */
    val backStack: SnapshotStateList<Screen> = mutableStateListOf<Screen>().apply { addAll(normalized(initial)) }

    val current: Screen get() = backStack.last()

    /**
     * Shows [screen]. If it's already on the stack (Settings → Fees → the
     * drawer's Fees again, say), pops back to it instead of stacking a
     * second copy — two entries with one key would share saved state.
     */
    fun navigate(screen: Screen) {
        val existing = backStack.indexOf(screen)
        if (existing >= 0) {
            backStack.removeRange(existing + 1, backStack.size)
        } else {
            backStack.add(screen)
        }
    }

    /**
     * Swaps the top screen for [screen] without adding history — the detail
     * pane following the converter's pair. A no-op on the converter.
     */
    fun replaceTop(screen: Screen) {
        if (backStack.size <= 1 || current == screen) return
        // A copy further down would share the new entry's saved state.
        backStack.remove(screen)
        backStack[backStack.lastIndex] = screen
    }

    /** Leaves the current screen. A no-op on the converter, which only the system back can close. */
    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    companion object {
        val Saver =
            listSaver<AppNavigator, String>(
                save = { navigator -> navigator.backStack.map(Screen::encode) },
                restore = { saved -> AppNavigator(saved.mapNotNull(::decodeScreen)) },
            )

        // The converter anchors the stack exactly once, at the bottom —
        // whatever a restore handed back.
        private fun normalized(screens: List<Screen>): List<Screen> =
            listOf(Screen.Converter) + screens.filterNot { it == Screen.Converter }.distinct()
    }
}

@Composable
fun rememberAppNavigator(): AppNavigator = rememberSaveable(saver = AppNavigator.Saver) { AppNavigator(listOf(Screen.Converter)) }
