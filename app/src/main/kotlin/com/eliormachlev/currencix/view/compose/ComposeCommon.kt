package com.eliormachlev.currencix.view.compose

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import com.eliormachlev.currencix.model.Currency

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

@Composable
fun FavoriteToggleIcon(
    active: Boolean,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
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
