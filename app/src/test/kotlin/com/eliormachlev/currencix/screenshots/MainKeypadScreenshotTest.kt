package com.eliormachlev.currencix.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.main.compose.MainKeypad
import com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Phase A screenshot proof-of-life — renders the MainKeypad in its two
// keyboard-type modes across the two Compose themes. When this passes in
// CI and the PNGs come back via `gh run download`, Phase B expands the
// matrix to every screen × en/he × LIGHT/DARK/OLED.
//
// Uses the composable-form `captureRoboImage { … }` from roborazzi-compose
// so no ActivityScenario / ComponentActivity manifest entry is required —
// the app's real MainActivity would otherwise be resolved and fail to
// start under Robolectric.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class MainKeypadScreenshotTest {
    private val callbacks =
        MainKeypadCallbacks(
            onDigit = {},
            onDecimal = {},
            onOperator = {},
            onPercent = {},
            onParens = {},
            onDelete = {},
            onDeleteLong = {},
        )

    @Test fun keypadBasicLight() = capture("keypad_basic_light", KeyboardType.BASIC, dark = false)

    @Test fun keypadBasicDark() = capture("keypad_basic_dark", KeyboardType.BASIC, dark = true)

    @Test fun keypadExpandedLight() = capture("keypad_expanded_light", KeyboardType.EXPANDED, dark = false)

    @Test fun keypadExpandedDark() = capture("keypad_expanded_dark", KeyboardType.EXPANDED, dark = true)

    private fun capture(
        name: String,
        type: KeyboardType,
        dark: Boolean,
    ) {
        captureRoboImage("$SCREENSHOT_DIR/$name.png") {
            AppTheme(dark = dark) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    MainKeypad(
                        keyboardType = type,
                        nextParen = '(',
                        callbacks = callbacks,
                    )
                }
            }
        }
    }

    companion object {
        // Where recordRoborazzi{Flavor}Debug writes the PNGs. The CI workflow
        // uploads this directory tree as an artifact for `gh run download`.
        const val SCREENSHOT_DIR = "build/outputs/roborazzi"
    }
}
