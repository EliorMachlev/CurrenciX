package com.eliormachlev.currencix.view.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.isRtlLanguage
import com.eliormachlev.currencix.util.rememberHapticOnClick

// Every flag in the app renders through this painter. painterResource parses
// each vector flag once and caches it app-wide, and a vector painter draws
// crisply at any size — unlike rasterising the Drawable to a bitmap at its
// intrinsic size, which allocated per currency change and blurred when drawn
// larger. It's also plain Compose: no embedded Android View per list row.

/**
 * The color of a feature icon's line (top bars' shortcuts, the drawer,
 * settings and ledger rows). With Material You on, the theme's primary, so
 * the icons take the wallpaper's color, moved toward the text color only as
 * far as it takes to read against the background; with it off, [plain],
 * the paper / ink icons' own color. Controls (back, ⋮, add, close,
 * chevrons) always keep the plain content color.
 */
@Composable
fun featureIconColor(plain: Color = MaterialTheme.colorScheme.onSurfaceVariant): Color {
    val scheme = MaterialTheme.colorScheme
    val colored =
        remember(scheme.primary, scheme.background, scheme.onBackground) {
            readableOn(scheme.primary, scheme.background, toward = scheme.onBackground)
        }
    return if (LocalDynamicColor.current) colored else plain
}

// WCAG's minimum for meaningful graphics such as icons.
private const val ICON_MIN_CONTRAST = 3f
private const val READABLE_STEP = 0.05f

/**
 * [color], or the first blend of it with [toward] that reaches
 * [ICON_MIN_CONTRAST] against [background]; [toward] itself if none does.
 */
internal fun readableOn(
    color: Color,
    background: Color,
    toward: Color,
): Color =
    generateSequence(0f) { it + READABLE_STEP }
        .takeWhile { it <= 1f }
        .map { lerp(color, toward, it) }
        .firstOrNull { contrastRatio(it, background) >= ICON_MIN_CONTRAST } ?: toward

// WCAG contrast ratio: (lighter + 0.05) / (darker + 0.05) of relative luminance.
internal fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
}

private const val CONTRAST_OFFSET = 0.05f

@Composable
fun Currency.flagPainter(): Painter = painterResource(flagRes)

// The picker needs a small rounded thumbnail, the quick-conversions header a
// larger square, and the chart layer wants none of that — so size + clip stay
// in the caller's Modifier chain rather than being baked in here. The default
// ContentScale.Fit, centered, matches the ImageView this replaced.
@Composable
fun CurrencyFlagImage(
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = currency.flagPainter(),
        contentDescription = null,
        modifier = modifier,
    )
}

/**
 * The sizes a flag is drawn at, each a rounded rectangle, so flags of one
 * role look the same wherever they appear.
 */
enum class FlagSize(
    val width: Dp,
    val height: Dp,
    corner: Dp,
) {
    /** Beside an ISO code in a chip. */
    Small(20.dp, 14.dp, 2.dp),

    /** A currency picker row. */
    Medium(24.dp, 17.dp, 2.dp),

    /** The converter's currency pills and the convert-selection sheet. */
    Large(28.dp, 20.dp, 2.dp),
    ;

    val shape: Shape = RoundedCornerShape(corner)
}

/** [currency]'s flag at one of the app's [FlagSize]s. */
@Composable
fun CurrencyFlag(
    currency: Currency,
    size: FlagSize,
    modifier: Modifier = Modifier,
) {
    CurrencyFlagImage(currency, modifier.size(width = size.width, height = size.height).clip(size.shape))
}

/** A row's supporting line under its title: small, in the muted color. */
@Composable
fun SupportingText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// Lays the wrapped content out in the app language's direction: right to
// left in Hebrew, Arabic and Farsi. AppTheme locks the app to left to right so
// digits, math and the converter never mirror; the prose surfaces (Settings,
// the sheets, the dialogs) opt back in with this, so their text, switches
// and icons sit the way the language reads. Anything numeric inside them
// that must keep its left-to-right geometry goes back in an [Ltr] island.
@Composable
fun ReadingDirection(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val direction = remember(context) { if (isRtlLanguage(context)) LayoutDirection.Rtl else LayoutDirection.Ltr }
    CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
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
    IconButton(onClick = rememberHapticOnClick(onClick)) {
        Icon(
            painter = painterResource(if (active) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
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
