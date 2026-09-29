package com.eliormachlev.currencix.view.main.compose

import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.TopBarAction
import com.eliormachlev.currencix.view.compose.onboarding.OnboardingAnchor
import com.eliormachlev.currencix.view.compose.onboarding.rememberOnboardingAnchorModifier
import com.eliormachlev.currencix.view.compose.screenTopBarColors
import com.eliormachlev.currencix.view.compose.theme.Wordmark

private val WORDMARK_TITLE_SIZE = 26.sp
private val DRAWER_ARROW_SIZE = 24.dp

/** The converter bar's shortcut actions — each also lives in the drawer. */
@Immutable
data class ConverterTopBarActions(
    val onTimeline: () -> Unit,
    val onCart: () -> Unit,
    val onQuickConversions: () -> Unit,
    val onHistoricalRates: () -> Unit,
)

/**
 * The converter's top bar: drawer button, the Currenci× wordmark, and the
 * four highest-frequency shortcuts as always-visible icons (everything else
 * lives in the drawer). [startReveal] / [onFirstFrame] drive the wordmark's
 * cold-start reveal and the splash-screen hand-off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterTopBar(
    drawerState: DrawerState,
    onToggleDrawer: () -> Unit,
    actions: ConverterTopBarActions,
    startReveal: Boolean,
    onFirstFrame: () -> Unit,
) {
    TopAppBar(
        title = {
            Wordmark(fontSize = WORDMARK_TITLE_SIZE, startReveal = startReveal, onFirstFrame = onFirstFrame)
        },
        navigationIcon = {
            val description =
                stringResource(if (drawerState.targetValue == DrawerValue.Open) R.string.desc_close_drawer else R.string.desc_open_drawer)
            IconButton(
                onClick = rememberHapticOnClick(onToggleDrawer),
                modifier =
                    rememberOnboardingAnchorModifier(OnboardingAnchor.Hamburger)
                        .semantics { contentDescription = description },
            ) {
                DrawerArrowIcon(drawerState)
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
 * The hamburger ↔ arrow morph, driven by the drawer's actual position: it
 * tracks a finger dragging the drawer and a fling's real velocity instead of
 * playing its own timed animation. The offset is collected into a float
 * state that only the Canvas's draw block reads, so a drag redraws this icon
 * and recomposes nothing.
 *
 * The sheet sits at -(its width) when closed and 0 when open (the app is
 * LTR-only). Rather than hard-code the sheet width, the closed offset is
 * learned as the smallest one seen: the offset never goes below it, and the
 * drawer starts closed, so the first value already is it.
 */
@Composable
private fun DrawerArrowIcon(drawerState: DrawerState) {
    val context = LocalContext.current
    val drawable = remember(context) { DrawerArrowDrawable(context) }
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
    val tint = LocalContentColor.current.toArgb()
    Canvas(Modifier.size(DRAWER_ARROW_SIZE)) {
        drawable.color = tint
        drawable.progress = progress.floatValue
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawIntoCanvas { canvas -> drawable.draw(canvas.nativeCanvas) }
    }
}
