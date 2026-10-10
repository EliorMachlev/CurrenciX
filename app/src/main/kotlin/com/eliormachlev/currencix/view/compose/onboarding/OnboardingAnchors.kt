package com.eliormachlev.currencix.view.compose.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo

/**
 * Anchor identifiers used by the first-run spotlight tour (#147). Kept as an
 * enum (not stringly-typed) so a typo in a Modifier call site is a compile
 * error and adding a new spotlight step is a single-place change.
 */
enum class OnboardingAnchor {
    FeeStamp,
    SwapFab,

    /** The converter top bar's drawer button. */
    Hamburger,
}

/**
 * Registry of anchor bounds captured via [onboardingAnchor].
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
 * with [onboardingAnchor].
 */
@Composable
fun ProvideOnboardingAnchors(content: @Composable () -> Unit) {
    val registry = remember { OnboardingAnchorRegistry() }
    CompositionLocalProvider(LocalOnboardingAnchors provides registry) {
        content()
    }
}

/**
 * Reports this element's window-space bounds into the enclosing
 * [OnboardingAnchorRegistry]. Does nothing where no registry is provided, so
 * it's safe on production UI without a conditional at the call site.
 */
fun Modifier.onboardingAnchor(anchor: OnboardingAnchor): Modifier = this then OnboardingAnchorElement(anchor)

private data class OnboardingAnchorElement(
    val anchor: OnboardingAnchor,
) : ModifierNodeElement<OnboardingAnchorNode>() {
    override fun create() = OnboardingAnchorNode(anchor)

    override fun update(node: OnboardingAnchorNode) {
        node.anchor = anchor
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "onboardingAnchor"
        properties["anchor"] = anchor
    }
}

// A node rather than a composable factory: it reads the registry when it is
// positioned, so tagging an element costs no composition of its own.
private class OnboardingAnchorNode(
    var anchor: OnboardingAnchor,
) : Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        currentValueOf(LocalOnboardingAnchors)?.report(anchor, coordinates.boundsInWindow())
    }
}
