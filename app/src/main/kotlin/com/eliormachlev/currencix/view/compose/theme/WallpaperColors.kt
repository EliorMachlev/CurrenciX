package com.eliormachlev.currencix.view.compose.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

// Material You's default contrast.
private const val STANDARD_CONTRAST = 0.0

/**
 * The home screen wallpaper's main colour, kept up to date as the wallpaper
 * changes; null when it has none to give (some live wallpapers), or while
 * [enabled] is off.
 */
@Composable
internal fun rememberWallpaperSeed(enabled: Boolean): Int? {
    val context = LocalContext.current
    var seed by remember(context, enabled) { mutableStateOf(if (enabled) wallpaperSeed(context) else null) }
    DisposableEffect(context, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val manager = WallpaperManager.getInstance(context)
        val listener =
            WallpaperManager.OnColorsChangedListener { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) seed = colors?.primaryColor?.toArgb()
            }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.removeOnColorsChangedListener(listener) }
    }
    return seed
}

private fun wallpaperSeed(context: Context): Int? =
    WallpaperManager
        .getInstance(context)
        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        ?.primaryColor
        ?.toArgb()

/**
 * The Material You palette of [seed], a wallpaper's main colour, as Android
 * builds its own from the wallpaper (the "tonal spot" style), light or
 * [dark]. Every role is set, so the one builder serves both modes (the
 * "fixed" roles, unset, are the same in both by definition).
 */
internal fun wallpaperColorScheme(
    seed: Int,
    dark: Boolean,
): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seed), dark, STANDARD_CONTRAST)
    val roles = MaterialDynamicColors()

    fun DynamicColor.on(scheme: DynamicScheme) = Color(getArgb(scheme))
    return lightColorScheme(
        primary = roles.primary.on(scheme),
        onPrimary = roles.onPrimary.on(scheme),
        primaryContainer = roles.primaryContainer.on(scheme),
        onPrimaryContainer = roles.onPrimaryContainer.on(scheme),
        inversePrimary = roles.inversePrimary.on(scheme),
        secondary = roles.secondary.on(scheme),
        onSecondary = roles.onSecondary.on(scheme),
        secondaryContainer = roles.secondaryContainer.on(scheme),
        onSecondaryContainer = roles.onSecondaryContainer.on(scheme),
        tertiary = roles.tertiary.on(scheme),
        onTertiary = roles.onTertiary.on(scheme),
        tertiaryContainer = roles.tertiaryContainer.on(scheme),
        onTertiaryContainer = roles.onTertiaryContainer.on(scheme),
        background = roles.background.on(scheme),
        onBackground = roles.onBackground.on(scheme),
        surface = roles.surface.on(scheme),
        onSurface = roles.onSurface.on(scheme),
        surfaceVariant = roles.surfaceVariant.on(scheme),
        onSurfaceVariant = roles.onSurfaceVariant.on(scheme),
        surfaceTint = roles.surfaceTint.on(scheme),
        inverseSurface = roles.inverseSurface.on(scheme),
        inverseOnSurface = roles.inverseOnSurface.on(scheme),
        error = roles.error.on(scheme),
        onError = roles.onError.on(scheme),
        errorContainer = roles.errorContainer.on(scheme),
        onErrorContainer = roles.onErrorContainer.on(scheme),
        outline = roles.outline.on(scheme),
        outlineVariant = roles.outlineVariant.on(scheme),
        scrim = roles.scrim.on(scheme),
        surfaceBright = roles.surfaceBright.on(scheme),
        surfaceDim = roles.surfaceDim.on(scheme),
        surfaceContainer = roles.surfaceContainer.on(scheme),
        surfaceContainerHigh = roles.surfaceContainerHigh.on(scheme),
        surfaceContainerHighest = roles.surfaceContainerHighest.on(scheme),
        surfaceContainerLow = roles.surfaceContainerLow.on(scheme),
        surfaceContainerLowest = roles.surfaceContainerLowest.on(scheme),
    )
}
