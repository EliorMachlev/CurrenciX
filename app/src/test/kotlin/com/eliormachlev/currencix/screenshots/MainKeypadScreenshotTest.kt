package com.eliormachlev.currencix.screenshots

import android.app.Application
import com.eliormachlev.currencix.view.main.compose.MainKeypad
import com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// MainKeypad in both keyboard-type modes, rendered across the full
// en/he × LIGHT/DARK/OLED matrix by the shared ScreenshotHarness.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class MainKeypadScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

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

    @Test fun keypadBasic() =
        shots.captureMatrix("keypad_basic") {
            MainKeypad(isExpandedKeypad = false, nextParen = '(', callbacks = callbacks)
        }

    @Test fun keypadExpanded() =
        shots.captureMatrix("keypad_expanded") {
            MainKeypad(isExpandedKeypad = true, nextParen = '(', callbacks = callbacks)
        }

    @Test fun keypadExpandedLargeFont() =
        shots.captureLargeFont("keypad_expanded") {
            MainKeypad(isExpandedKeypad = true, nextParen = '(', callbacks = callbacks)
        }
}
