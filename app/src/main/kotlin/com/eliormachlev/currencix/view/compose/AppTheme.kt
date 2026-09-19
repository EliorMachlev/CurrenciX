package com.eliormachlev.currencix.view.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.eliormachlev.currencix.view.compose.theme.CurrenciXDarkColors
import com.eliormachlev.currencix.view.compose.theme.CurrenciXLightColors
import com.eliormachlev.currencix.view.compose.theme.CurrenciXShapes
import com.eliormachlev.currencix.view.compose.theme.CurrenciXTypography

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
// the manifest for any surviving XML surfaces.
@Composable
fun AppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) CurrenciXDarkColors else CurrenciXLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = CurrenciXTypography,
        shapes = CurrenciXShapes,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                content = content,
            )
        }
    }
}
