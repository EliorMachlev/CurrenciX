package com.eliormachlev.currencix.view.cart.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.view.main.spinner.SearchableSpinnerDialog

private val CHIP_HEIGHT: Dp = 44.dp
private val CHIP_RADIUS: Dp = 999.dp
private val CHIP_HORIZONTAL_PADDING: Dp = 14.dp

// Rectangular flag matching the picker's 24×17 aspect so both surfaces read
// the same.
private val FLAG_WIDTH: Dp = 24.dp
private val FLAG_HEIGHT: Dp = 17.dp
private val FLAG_CORNER_RADIUS: Dp = 2.dp
private val FLAG_GAP: Dp = 10.dp
private val CHEVRON_SIZE: Dp = 14.dp
private val CHEVRON_GAP: Dp = 4.dp

/**
 * Pill-style currency picker used in the cart footer, sized to match the
 * hero card's [CurrencyPill] on the main screen so both surfaces read the
 * same. Tapping opens the shared [SearchableSpinnerDialog] wired to the
 * cart's currency setters, with [disabledCurrency] greyed out so the two
 * sides of the pair stay distinct.
 */
@Composable
fun CartCurrencyChip(
    fragmentManager: FragmentManager,
    currency: Currency?,
    disabledCurrency: Currency?,
    onCurrencyPicked: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val flagPainter = remember(currency) { currency?.flag(context)?.let(::drawableToPainter) }
    Row(
        modifier
            .height(CHIP_HEIGHT)
            .clip(RoundedCornerShape(CHIP_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .hapticClickable(enabled = currency != null) {
                SearchableSpinnerDialog(context)
                    .apply {
                        setDisabledCurrency(disabledCurrency)
                        onRateClicked = { rate, _ -> onCurrencyPicked(rate.currency) }
                    }.show(fragmentManager, null)
            }.padding(horizontal = CHIP_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (flagPainter != null) {
            Image(
                painter = flagPainter,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(width = FLAG_WIDTH, height = FLAG_HEIGHT)
                        .clip(RoundedCornerShape(FLAG_CORNER_RADIUS)),
            )
            Spacer(Modifier.width(FLAG_GAP))
        }
        Text(
            text = currency?.iso4217Alpha().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(CHEVRON_GAP))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(CHEVRON_SIZE),
        )
    }
}

private fun drawableToPainter(drawable: Drawable): Painter {
    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return BitmapPainter(bmp.asImageBitmap())
}
