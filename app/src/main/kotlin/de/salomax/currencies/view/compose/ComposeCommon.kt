package de.salomax.currencies.view.compose

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import de.salomax.currencies.model.Currency

// Renders a Currency's flag drawable via an ImageView. Callers supply the
// Modifier chain (size, clip) so the same primitive works for the picker's
// small rounded thumbnail and the quick-conversions header's larger square.
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

// Forces left-to-right layout for the wrapped content. Math and currency
// conversion previews read L→R in every locale — under RTL the operator would
// visually flip which is confusing.
@Composable
fun LtrBox(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        content()
    }
}
