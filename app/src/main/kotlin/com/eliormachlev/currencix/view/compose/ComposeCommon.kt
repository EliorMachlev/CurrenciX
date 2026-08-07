package com.eliormachlev.currencix.view.compose

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
