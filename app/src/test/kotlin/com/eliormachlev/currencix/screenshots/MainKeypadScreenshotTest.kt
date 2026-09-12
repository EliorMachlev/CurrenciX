package com.eliormachlev.currencix.screenshots

import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.view.main.compose.MainKeypad
import com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// MainKeypad in both keyboard-type modes, rendered across the full
// en/he × LIGHT/DARK/OLED matrix by the shared ScreenshotHarness.
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

    @Test fun keypadBasic() =
        captureMatrix("keypad_basic") {
            MainKeypad(keyboardType = KeyboardType.BASIC, nextParen = '(', callbacks = callbacks)
        }

    @Test fun keypadExpanded() =
        captureMatrix("keypad_expanded") {
            MainKeypad(keyboardType = KeyboardType.EXPANDED, nextParen = '(', callbacks = callbacks)
        }
}
