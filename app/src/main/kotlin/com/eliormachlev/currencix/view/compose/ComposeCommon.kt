package com.eliormachlev.currencix.view.compose

import android.app.Activity
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.rememberHapticOnClick

// The picker needs a small rounded thumbnail, the quick-conversions header a
// larger square, and the chart layer wants none of that — so size + clip stay
// in the caller's Modifier chain rather than being baked in here.
@Composable
fun CurrencyFlagImage(
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                adjustViewBounds = true
                contentDescription = null
            }
        },
        update = { iv -> iv.setImageDrawable(currency.flag(iv.context)) },
        modifier = modifier,
    )
}

// Forces left-to-right layout for the wrapped content. Math previews and the
// Vico chart's touch coordinates read L→R in every locale; under an RTL app
// locale they would otherwise mirror.
@Composable
fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        content()
    }
}

// Filled-vs-outlined heart IconButton used for the picker's star toggle and
// the cart's pin toggle. Same drawable pair, same tint semantics, so both
// sites read the same way to users.
// Fires [onTap] for taps no child of the modified node consumed. `composed`
// + `rememberUpdatedState` keeps the captured lambda in sync across
// recompositions without relaunching the pointer-input coroutine.
fun Modifier.onBackgroundTap(onTap: () -> Unit): Modifier =
    composed {
        val current by rememberUpdatedState(onTap)
        pointerInput(Unit) {
            detectTapGestures(onTap = { current() })
        }
    }

// Returns the vertical overlap (in dp) between AppCompat's ActionBar
// container and the hosting ComposeView, so screens can pad their Compose
// root by exactly that amount and sit flush below the toolbar. Measures the
// bar's real laid-out bottom (rather than resolving `?attr/actionBarSize` +
// status-bar insets by hand) so it stays correct across edge-to-edge, split
// screen, and any theme that resizes the ActionBar. Applied on the three
// activities that host Compose directly: MainActivity, CartActivity,
// TimelineActivity.
@Composable
fun rememberActionBarTopPadding(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var overlapPx by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val activity = view.context as? Activity
        val actionBar = activity?.findViewById<View>(androidx.appcompat.R.id.action_bar_container)

        fun recompute() {
            if (actionBar == null || !view.isAttachedToWindow) return
            val abLoc = IntArray(2).also(actionBar::getLocationInWindow)
            val vLoc = IntArray(2).also(view::getLocationInWindow)
            overlapPx = (abLoc[1] + actionBar.height - vLoc[1]).coerceAtLeast(0)
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> recompute() }
        view.addOnLayoutChangeListener(listener)
        actionBar?.addOnLayoutChangeListener(listener)
        recompute()
        onDispose {
            view.removeOnLayoutChangeListener(listener)
            actionBar?.removeOnLayoutChangeListener(listener)
        }
    }
    return with(density) { overlapPx.toDp() }
}

@Composable
fun FavoriteToggleIcon(
    active: Boolean,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = rememberHapticOnClick(onClick)) {
        Icon(
            imageVector = if (active) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = contentDescription,
            tint =
                if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
