package com.eliormachlev.currencix.view.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
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
// Uses MaterialExpressiveTheme (M3 Expressive) so every component picks up the
// expressive MotionScheme (bouncier springs, richer transitions) and expressive
// shape/typography defaults without per-callsite opt-in.
//
// Wraps in a transparent Surface so LocalContentColor is set to onSurface for
// every ComposeView call site (dialogs, activity roots, popups). Without this,
// Text defaults to Color.Black — invisible on dark AlertDialog backgrounds.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) CurrenciXDarkColors else CurrenciXLightColors
    MaterialExpressiveTheme(
        colorScheme = colors,
        typography = CurrenciXTypography,
        shapes = CurrenciXShapes,
        motionScheme = MotionScheme.expressive(),
    ) {
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = content,
        )
    }
}
