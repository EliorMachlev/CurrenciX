package de.salomax.currencies.view.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Single wrapper for every Compose surface in the app. Callers should never
// instantiate their own MaterialTheme — hoist to this so palette/dynamic-color
// decisions live in one place.
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
    val context = LocalContext.current
    val colors =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) darkColorScheme() else lightColorScheme()
        }
    MaterialTheme(colorScheme = colors) {
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = content,
        )
    }
}
