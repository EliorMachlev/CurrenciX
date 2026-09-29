package com.eliormachlev.currencix.view.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
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

/**
 * Makes a currency pill a shared element: when a screen with a matching pill
 * ([PillSide] + currency) opens or closes, the pill moves and resizes into
 * its counterpart instead of fading with its screen. It follows the screen
 * transition, so the predictive back gesture scrubs it too.
 *
 * A plain [Modifier] outside the nav host (screenshot tests, previews),
 * while no currency is set, and on a two-pane window — where the converter
 * and the cart sit side by side, so there's nothing to fly between.
 */
@Composable
fun sharedCurrencyPillModifier(
    side: PillSide,
    currency: Currency?,
): Modifier {
    if (currency == null || LocalTwoPaneWindow.current) return Modifier
    val sharedScope = LocalSharedTransitionScope.current ?: return Modifier
    val visibilityScope = LocalScreenVisibilityScope.current ?: return Modifier
    return with(sharedScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(SharedPillKey(side, currency)),
            animatedVisibilityScope = visibilityScope,
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
