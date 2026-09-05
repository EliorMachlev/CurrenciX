package com.eliormachlev.currencix.view.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
// Nested Surfaces (e.g. TimelineScreen's Surface with a real background) are
// fine and simply override.
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
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = content,
        )
    }
}
