package com.eliormachlev.currencix.view.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Which pane a screen takes on a wide window: the converter is the [List]
 * pane that stays put on the left; the timeline and the cart are [Detail]
 * screens that open beside it instead of over it. Screens with no role
 * (settings and its sub-screens) always cover the whole window.
 */
enum class PaneRole { List, Detail }

/** Material's "expanded" width class: from here on, a detail screen opens beside the converter. */
internal val TWO_PANE_MIN_WIDTH: Dp = 840.dp

// The converter pane: a fraction of the window, kept between a phone-sized
// minimum (its keypad needs the width) and a maximum past which the extra
// room is better spent on the chart.
private const val LIST_PANE_FRACTION = 0.4f
private val LIST_PANE_MIN_WIDTH: Dp = 360.dp
private val LIST_PANE_MAX_WIDTH: Dp = 480.dp

private const val PANE_ROLE_KEY = "com.eliormachlev.currencix.paneRole"

/** The pane this entry is shown in, while [TwoPaneScene] is on screen; null in the single-pane layout. */
val LocalPaneRole = staticCompositionLocalOf<PaneRole?> { null }

/** Entry metadata that tells [TwoPaneSceneStrategy] which pane [role] a screen may take. */
internal fun paneMetadata(role: PaneRole?): Map<String, Any> = if (role == null) emptyMap() else mapOf(PANE_ROLE_KEY to role)

private val NavEntry<*>.paneRole: PaneRole? get() = metadata[PANE_ROLE_KEY] as? PaneRole

/** The converter's side, and the side the timeline and the cart open on. */
internal val Screen.paneRole: PaneRole?
    get() =
        when (this) {
            Screen.Converter -> PaneRole.List
            is Screen.Timeline, is Screen.Cart -> PaneRole.Detail
            Screen.Settings, Screen.Fees, Screen.Backup -> null
        }

/**
 * List + detail side by side. Keyed by the detail entry, so opening another
 * detail screen (or the converter's pair changing the timeline) runs the
 * usual screen transition — for the detail pane only, since the list entry is
 * in both scenes and NavDisplay moves it as a shared element.
 */
private class TwoPaneScene<T : Any>(
    private val list: NavEntry<T>,
    private val detail: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
) : Scene<T> {
    override val key: Any = TwoPaneKey(detail.contentKey)
    override val entries: List<NavEntry<T>> = listOf(list, detail)

    override val content: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val listWidth = (maxWidth * LIST_PANE_FRACTION).coerceIn(LIST_PANE_MIN_WIDTH, LIST_PANE_MAX_WIDTH)
            Row(Modifier.fillMaxSize()) {
                Pane(PaneRole.List, Modifier.width(listWidth)) { list.Content() }
                VerticalDivider()
                Pane(PaneRole.Detail, Modifier.weight(1f)) { detail.Content() }
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is TwoPaneScene<*> && list == other.list && detail == other.detail && previousEntries == other.previousEntries

    override fun hashCode(): Int = (list.hashCode() * HASH_PRIME + detail.hashCode()) * HASH_PRIME + previousEntries.hashCode()

    private data class TwoPaneKey(
        val detailKey: Any,
    )

    private companion object {
        const val HASH_PRIME = 31
    }
}

@Composable
private fun Pane(
    role: PaneRole,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPaneRole provides role) {
        Box(modifier.fillMaxHeight()) { content() }
    }
}

/**
 * Shows the top screen beside the converter when the window is wide enough
 * and the top screen is a [PaneRole.Detail]; otherwise defers to the next
 * strategy (one screen at a time).
 */
internal class TwoPaneSceneStrategy<T : Any>(
    private val isWide: Boolean,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!isWide) return null
        val detail = entries.lastOrNull()?.takeIf { it.paneRole == PaneRole.Detail } ?: return null
        val below = entries.dropLast(1)
        val list = below.lastOrNull { it.paneRole == PaneRole.List } ?: return null
        return TwoPaneScene(list, detail, previousEntries = below)
    }
}

/** True when the window is wide enough for [TwoPaneSceneStrategy]'s side-by-side layout. */
@Composable
internal fun isTwoPaneWindow(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { widthPx.toDp() >= TWO_PANE_MIN_WIDTH }
}

/**
 * Whether the window is wide enough for two panes. The converter and the
 * cart are then on screen together, so their pills don't fly between them.
 */
val LocalTwoPaneWindow = staticCompositionLocalOf { false }
