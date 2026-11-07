package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.TopBarAction
import com.eliormachlev.currencix.view.compose.onboarding.OnboardingAnchor
import com.eliormachlev.currencix.view.compose.onboarding.onboardingAnchor
import com.eliormachlev.currencix.view.compose.screenTopBarColors
import com.eliormachlev.currencix.view.compose.theme.Wordmark
import com.eliormachlev.currencix.view.compose.theme.WordmarkReveal

private val WORDMARK_TITLE_SIZE = 26.sp
private val DRAWER_ICON_SIZE = 24.dp

// How far the two drawer icons turn as they cross-fade: the menu a quarter
// turn away, the arrow in from a quarter turn.
private const val DRAWER_ICON_TURN = 90f

/** The converter bar's shortcut actions — each also lives in the drawer. */
@Immutable
data class ConverterTopBarActions(
    val onTimeline: () -> Unit,
    val onCart: () -> Unit,
    val onQuickConversions: () -> Unit,
    val onHistoricalRates: () -> Unit,
    val onRefresh: () -> Unit,
)

/**
 * The converter's top bar: drawer button, the Currenci× wordmark, and the
 * four highest-frequency shortcuts as always-visible icons (everything else
 * lives in the drawer). [reveal] is the wordmark's cold-start reveal and
 * its hand-off with the splash screen; its logo turns while [isRefreshing],
 * and tapping it refreshes the rates.
 */
@Composable
fun ConverterTopBar(
    drawerState: DrawerState,
    onToggleDrawer: () -> Unit,
    actions: ConverterTopBarActions,
    reveal: WordmarkReveal,
    isRefreshing: Boolean,
) {
    TopAppBar(
        title = {
            Wordmark(
                fontSize = WORDMARK_TITLE_SIZE,
                reveal = reveal,
                turning = isRefreshing,
                onClick = actions.onRefresh,
            )
        },
        navigationIcon = {
            val description =
                stringResource(if (drawerState.targetValue == DrawerValue.Open) R.string.desc_close_drawer else R.string.desc_open_drawer)
            IconButton(
                onClick = rememberHapticOnClick(onToggleDrawer),
                modifier =
                    Modifier
                        .onboardingAnchor(OnboardingAnchor.Hamburger)
                        .semantics { contentDescription = description },
            ) {
                DrawerIcon(drawerState)
            }
        },
        actions = {
            TopBarAction(painterResource(R.drawable.ic_timeline), stringResource(R.string.menu_timeline), actions.onTimeline)
            TopBarAction(painterResource(R.drawable.ic_cart), stringResource(R.string.cart_title), actions.onCart)
            TopBarAction(
                painterResource(R.drawable.ic_table),
                stringResource(R.string.menu_quick_conversions),
                actions.onQuickConversions,
            )
            TopBarAction(
                painterResource(R.drawable.ic_history),
                stringResource(R.string.menu_historical_rates),
                actions.onHistoricalRates,
            )
        },
        colors = screenTopBarColors(),
    )
}

/**
 * The menu ↔ arrow icon, driven by the drawer's actual position: it tracks a
 * finger dragging the drawer and a fling's real velocity instead of playing
 * its own timed animation. The two icons cross-fade and turn a quarter turn
 * as the drawer opens. The offset is collected into a float state that only
 * the layers read, so a drag redraws this icon and recomposes nothing.
 *
 * The sheet sits at -(its width) when closed and 0 when open, in either
 * direction (the drawer's offset follows the layout direction). The arrow
 * mirrors with the layout, so it points the way the drawer closes: left in
 * English, right in Hebrew. Rather than hard-code the sheet width, the
 * closed offset is learned as the smallest one seen: the offset never goes
 * below it, and the drawer starts closed, so the first value already is it.
 */
@Composable
private fun DrawerIcon(drawerState: DrawerState) {
    val turnDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(drawerState) {
        var closedOffset = Float.NaN
        snapshotFlow { drawerState.currentOffset }
            .collect { offset ->
                if (offset.isNaN()) return@collect // anchors not measured yet
                closedOffset = if (closedOffset.isNaN()) offset else minOf(closedOffset, offset)
                progress.floatValue =
                    if (closedOffset < 0f) {
                        (1f - offset / closedOffset).coerceIn(0f, 1f)
                    } else {
                        // Restored straight into the open state: no closed
                        // offset seen yet, so fall back to the settled value.
                        if (drawerState.currentValue == DrawerValue.Open) 1f else 0f
                    }
            }
    }
    // The button's own description names the action, so the icons have none.
    Box(Modifier.size(DRAWER_ICON_SIZE)) {
        Icon(
            painter = painterResource(R.drawable.ic_menu),
            contentDescription = null,
            modifier =
                Modifier.graphicsLayer {
                    alpha = 1f - progress.floatValue
                    rotationZ = turnDirection * DRAWER_ICON_TURN * progress.floatValue
                },
        )
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = null,
            modifier =
                Modifier.graphicsLayer {
                    alpha = progress.floatValue
                    rotationZ = turnDirection * DRAWER_ICON_TURN * (progress.floatValue - 1f)
                },
        )
    }
}
