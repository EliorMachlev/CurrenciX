package com.eliormachlev.currencix.view.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.eliormachlev.currencix.model.Currency

/** Which side of a conversion a currency pill stands for. */
enum class PillSide { FROM, TO }

// Two pills are the same shared element only when they show the same
// currency on the same side — a cart that keeps its own pair doesn't pull
// the converter's pills across the screen.
private data class SharedPillKey(
    val side: PillSide,
    val currency: Currency,
)

/** What a pill needs to fly between two screens; see [rememberPillTransition]. */
class PillTransition internal constructor(
    internal val scope: SharedTransitionScope,
    internal val visibility: AnimatedVisibilityScope,
    internal val state: SharedTransitionScope.SharedContentState,
)

/**
 * The shared-element transition of the pill showing [currency] on [side], for
 * [sharedCurrencyPill]. Null — the pill just stays where it is — outside the
 * nav host (screenshot tests, previews), while no currency is set, and on a
 * two-pane window, where the converter and the cart sit side by side and
 * there's nothing to fly between.
 */
@Composable
fun rememberPillTransition(
    side: PillSide,
    currency: Currency?,
): PillTransition? {
    if (currency == null || LocalTwoPaneWindow.current) return null
    val sharedScope = LocalSharedTransitionScope.current ?: return null
    val visibilityScope = LocalScreenVisibilityScope.current ?: return null
    val state = with(sharedScope) { rememberSharedContentState(SharedPillKey(side, currency)) }
    return remember(sharedScope, visibilityScope, state) { PillTransition(sharedScope, visibilityScope, state) }
}

/**
 * Makes a currency pill a shared element: when a screen with a matching pill
 * ([PillSide] + currency) opens or closes, the pill moves and resizes into
 * its counterpart instead of fading with its screen. It follows the screen
 * transition.
 */
fun Modifier.sharedCurrencyPill(transition: PillTransition?): Modifier {
    transition ?: return this
    return with(transition.scope) {
        sharedBounds(
            sharedContentState = transition.state,
            animatedVisibilityScope = transition.visibility,
            // Re-lays the pill out at each in-between size, so the flag and
            // code stay crisp instead of stretching.
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            // In step with the screen transition. The default (a soft spring)
            // outlasts it and would keep the whole transition running on.
            boundsTransform = { _, _ -> ScreenMotion.decelerate() },
            enter = fadeIn(ScreenMotion.decelerate()),
            exit = fadeOut(ScreenMotion.decelerate()),
        )
    }
}
