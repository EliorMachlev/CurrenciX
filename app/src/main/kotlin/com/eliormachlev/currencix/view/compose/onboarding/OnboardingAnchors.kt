package com.eliormachlev.currencix.view.compose.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Anchor identifiers used by the first-run spotlight tour (#147). Kept as an
 * enum (not stringly-typed) so a typo in a Modifier call site is a compile
 * error and adding a new spotlight step is a single-place change.
 */
enum class OnboardingAnchor {
    FeeStamp,
    SwapFab,

    /**
     * The ActionBar hamburger sits outside the Compose tree, so the spotlight
     * overlay synthesizes a fixed hint region for this anchor rather than
     * reading real bounds from a modifier. Kept in the enum so the tour step
     * can request it the same way as the in-tree anchors.
     */
    Hamburger,
}

/**
 * Registry of anchor bounds captured via [rememberOnboardingAnchorModifier].
 * A small mutable state map so [Spotlight] observes changes without every
 * consumer having to re-hoist a callback per anchor.
 *
 * Bounds are reported in window coordinates so the spotlight overlay — which
 * fills the whole window — can render them without a common ancestor.
 * Installed at the top of the Compose tree via [ProvideOnboardingAnchors].
 */
class OnboardingAnchorRegistry {
    private val bounds = mutableStateMapOf<OnboardingAnchor, Rect>()

    internal fun report(
        anchor: OnboardingAnchor,
        rect: Rect,
    ) {
        // Rect equality skips redundant writes so we don't invalidate the
        // spotlight overlay every frame during scroll if the anchor didn't
        // actually move.
        if (bounds[anchor] != rect) bounds[anchor] = rect
    }

    fun boundsOf(anchor: OnboardingAnchor): Rect? = bounds[anchor]
}

val LocalOnboardingAnchors = compositionLocalOf<OnboardingAnchorRegistry?> { null }

/**
 * Installs a fresh [OnboardingAnchorRegistry] into the composition. Call once
 * at the top of the screen tree; children can then tag their anchor targets
 * with [rememberOnboardingAnchorModifier].
 */
@Composable
fun ProvideOnboardingAnchors(content: @Composable () -> Unit) {
    val registry = remember { OnboardingAnchorRegistry() }
    CompositionLocalProvider(LocalOnboardingAnchors provides registry) {
        content()
    }
}

/**
 * Returns a Modifier that reports its composable's window-space bounds into
 * the enclosing [OnboardingAnchorRegistry], if any. No-op (returns
 * [Modifier]) when no registry is provided — safe to sprinkle on production
 * UI without a conditional wrap on the call site.
 */
@Composable
fun rememberOnboardingAnchorModifier(anchor: OnboardingAnchor): Modifier {
    val registry = LocalOnboardingAnchors.current ?: return Modifier
    return Modifier.onGloballyPositioned { coords ->
        registry.report(anchor, coords.boundsInWindow())
    }
}
