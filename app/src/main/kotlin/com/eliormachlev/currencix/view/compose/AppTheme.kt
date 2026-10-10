package com.eliormachlev.currencix.view.compose

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.eliormachlev.currencix.view.compose.theme.CurrenciXDarkColors
import com.eliormachlev.currencix.view.compose.theme.CurrenciXLightColors
import com.eliormachlev.currencix.view.compose.theme.CurrenciXShapes
import com.eliormachlev.currencix.view.compose.theme.CurrenciXTypography

/**
 * Whether the enclosing [AppTheme] uses wallpaper colors, so an [AppTheme]
 * nested inside it (a sheet, a dialog) keeps the same palette.
 */
val LocalDynamicColor = compositionLocalOf { false }

// Single wrapper for every Compose surface in the app. Callers should never
// instantiate their own MaterialTheme — hoist to this so palette / typography /
// shape decisions live in one place.
//
// Wraps in a transparent Surface so LocalContentColor is set to onSurface for
// every ComposeView call site (dialogs, activity roots, popups). Without this,
// Text defaults to Color.Black — invisible on dark AlertDialog backgrounds.
//
// Locks the layout direction to LTR so RTL locales (Hebrew, Arabic, …) only
// swap translated strings, not the whole layout — currency digits and math
// read L→R universally, and mirroring the pill / keypad / receipt geometry
// makes those numbers harder to parse. Paired with `supportsRtl="false"` in
// the manifest for any surviving XML surfaces. The prose surfaces — Settings,
// the sheets and the dialogs — opt back into the language's direction with
// ReadingDirection (see architecture.md, "Right-to-left languages").
//
// [dynamicColor] swaps the paper / ink palette for the wallpaper's (Material
// You). Brand accents that aren't scheme colors (the fee stamp, status
// pills) stay as they are.
@Composable
fun AppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = LocalDynamicColor.current,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = remember(context, dark, dynamicColor) { colorScheme(context, dark, dynamicColor) }
    MaterialTheme(
        colorScheme = colors,
        typography = CurrenciXTypography,
        shapes = CurrenciXShapes,
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Ltr,
            LocalDynamicColor provides dynamicColor,
        ) {
            Surface(
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                content = content,
            )
        }
    }
}

/**
 * [AppTheme] for the prose screens — Settings, Fees, Backup: the same
 * palette, laid out the way the app's language reads ([ReadingDirection]).
 * Sheets set the direction inside their own window instead
 * (LedgerBottomSheet, and so every prompt built on it).
 */
@Composable
fun ProseTheme(content: @Composable () -> Unit) {
    AppTheme { ReadingDirection(content) }
}

private fun colorScheme(
    context: Context,
    dark: Boolean,
    dynamicColor: Boolean,
): ColorScheme =
    when {
        dynamicColor ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> CurrenciXDarkColors
        else -> CurrenciXLightColors
    }
