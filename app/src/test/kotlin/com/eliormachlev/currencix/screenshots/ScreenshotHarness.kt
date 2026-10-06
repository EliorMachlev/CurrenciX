package com.eliormachlev.currencix.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.eliormachlev.currencix.util.registerActivityRule
import com.eliormachlev.currencix.view.compose.AppTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Locale

// Where recordRoborazzi{Flavor}Debug writes PNGs. The CI workflow uploads
// this directory tree as the `screenshots-<sha>` artifact.
internal const val SCREENSHOT_DIR = "build/outputs/roborazzi"

// Per-cell axes for the render matrix: BCP-47 language tag drives Locale +
// LayoutDirection, ThemeMode drives dark palette + OLED background override.
internal enum class Locale2(
    val tag: String,
    val dir: LayoutDirection,
) {
    EN("en", LayoutDirection.Ltr),
    HE("he", LayoutDirection.Rtl),
}

internal enum class ThemeMode(
    val dark: Boolean,
    val oled: Boolean,
) {
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

/**
 * Renders the screenshot matrix on a manual clock. Compose is set up once;
 * each cell (locale × theme) swaps in through state, gets [SETTLE_MILLIS] of
 * frames — entrances finish, and an infinite animation (a loading bar, the
 * shimmer) runs a fixed stretch instead of keeping the capture from ever
 * settling — and is captured.
 *
 * Sheets and dialogs live in windows of their own, so a cell showing one is
 * captured as the whole screen; anything else as just the content.
 */
class ScreenshotRule : TestRule {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val chain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement = chain.apply(base, description)

    /** Writes `<name>_<loc>_<theme>.png` for every cell of [MATRIX] into [SCREENSHOT_DIR]. */
    fun captureMatrix(
        name: String,
        content: @Composable () -> Unit,
    ) = capture(MATRIX.map { (locale, theme) -> Cell(locale, theme) }, name, content)

    /**
     * Writes `<name>_large_font.png`: English, light, text at 200 % — the
     * largest system font size — to catch clipped or overlapping text.
     */
    fun captureLargeFont(
        name: String,
        content: @Composable () -> Unit,
    ) = capture(listOf(Cell(Locale2.EN, ThemeMode.LIGHT, LARGE_FONT_SCALE)), name, content)

    // captureScreenRoboImage (the whole window, for dialogs) is still experimental in Roborazzi.
    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        cells: List<Cell>,
        name: String,
        content: @Composable () -> Unit,
    ) {
        val current = mutableStateOf(cells.first())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val cell = current.value
            key(cell) {
                FontScaled(cell.fontScale) {
                    Box(Modifier.testTag(ROOT_TAG)) { MatrixCell(cell.locale, cell.theme, content) }
                }
            }
        }
        cells.forEach { cell ->
            current.value = cell
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
            compose.waitForIdle()
            val file = "$SCREENSHOT_DIR/${name}_${cell.suffix}.png"
            if (compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) {
                captureScreenRoboImage(file)
            } else {
                compose.onNodeWithTag(ROOT_TAG).captureRoboImage(file)
            }
        }
    }

    private data class Cell(
        val locale: Locale2,
        val theme: ThemeMode,
        val fontScale: Float = 1f,
    ) {
        val suffix: String
            get() = if (fontScale == 1f) "${locale.name.lowercase()}_${theme.name.lowercase()}" else "large_font"
    }

    private companion object {
        const val ROOT_TAG = "screenshot_root"

        // Long enough for every entrance and sheet slide-in; a fixed stretch
        // of any animation that never ends.
        const val SETTLE_MILLIS = 1_000L

        // Android's largest font-size setting.
        const val LARGE_FONT_SCALE = 2f
    }
}

@Composable
private fun FontScaled(
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    if (fontScale == 1f) return content()
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
}
