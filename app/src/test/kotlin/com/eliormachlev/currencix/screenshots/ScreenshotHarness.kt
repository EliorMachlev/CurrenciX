package com.eliormachlev.currencix.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.eliormachlev.currencix.view.compose.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.Locale

// Where recordRoborazzi{Flavor}Debug writes PNGs. The CI workflow uploads
// this directory tree as the `screenshots-<sha>` artifact.
internal const val SCREENSHOT_DIR = "build/outputs/roborazzi"

// Per-cell axes for the render matrix: BCP-47 language tag drives Locale +
// LayoutDirection, ThemeMode drives dark palette + OLED background override.
internal enum class Locale2(val tag: String, val dir: LayoutDirection) {
    EN("en", LayoutDirection.Ltr),
    HE("he", LayoutDirection.Rtl),
}

internal enum class ThemeMode(val dark: Boolean, val oled: Boolean) {
    LIGHT(dark = false, oled = false),
    DARK(dark = true, oled = false),
    OLED(dark = true, oled = true),
}

// Every axis × axis combo we render per composable. Kept as a top-level
// list so a test can iterate it with a single loop.
internal val MATRIX: List<Pair<Locale2, ThemeMode>> =
    Locale2.entries.flatMap { loc -> ThemeMode.entries.map { loc to it } }

// Applies locale + theme + OLED override + a background-filled Box so the
// captured PNG has the same paper/ink/OLED backdrop as the real app. Callers
// pass the composable-under-test as `content`.
@Composable
internal fun MatrixCell(
    locale: Locale2,
    theme: ThemeMode,
    content: @Composable () -> Unit,
) {
    Locale.setDefault(Locale.forLanguageTag(locale.tag))
    CompositionLocalProvider(LocalLayoutDirection provides locale.dir) {
        AppTheme(dark = theme.dark) {
            // OLED = pureBlack surface applied over the dark palette. In the
            // real app this comes from the Activity's XML theme; in tests we
            // paint a black Surface + a black Box so downstream composables
            // that read colorScheme.background still get the dark palette's
            // ink tones for foregrounds, but the backdrop is true black.
            val bg = if (theme.oled) Color.Black else MaterialTheme.colorScheme.background
            Surface(color = bg, contentColor = MaterialTheme.colorScheme.onSurface) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(bg),
                ) {
                    content()
                }
            }
        }
    }
}

// Renders a composable once per matrix cell, writing `<name>_<loc>_<theme>.png`
// into SCREENSHOT_DIR. The composable-form captureRoboImage sidesteps
// ActivityScenario — no manifest activity needs to resolve.
internal fun captureMatrix(
    name: String,
    content: @Composable () -> Unit,
) {
    MATRIX.forEach { (locale, theme) ->
        val fileName = "${name}_${locale.name.lowercase()}_${theme.name.lowercase()}.png"
        captureRoboImage("$SCREENSHOT_DIR/$fileName") {
            MatrixCell(locale, theme, content)
        }
    }
}
