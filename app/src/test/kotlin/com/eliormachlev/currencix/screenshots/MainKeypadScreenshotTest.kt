package com.eliormachlev.currencix.screenshots

import android.app.Application
import com.eliormachlev.currencix.view.main.compose.MainKeypad
import com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks
import com.eliormachlev.currencix.viewmodel.main.KeyAvailability
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// MainKeypad in both keyboard-type modes (at rest: ")" and = dimmed;
// mid-calculation: "." and % dimmed after a ")"), and kept to one side on
// an unfolded foldable, rendered across the full
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
            onOpenParen = {},
            onCloseParen = {},
            onDelete = {},
            onClear = {},
            onEquals = {},
        )

    @Test fun keypadBasic() =
        shots.captureMatrix("keypad_basic") {
            MainKeypad(isExpandedKeypad = false, keys = KeyAvailability(), callbacks = callbacks)
        }

    @Test fun keypadExpanded() =
        shots.captureMatrix("keypad_expanded") {
            MainKeypad(isExpandedKeypad = true, keys = KeyAvailability(), callbacks = callbacks)
        }

    @Test fun keypadExpandedLargeFont() =
        shots.captureLargeFont("keypad_expanded") {
            MainKeypad(isExpandedKeypad = true, keys = KeyAvailability(), callbacks = callbacks)
        }

    @Test fun keypadMidCalculation() =
        shots.captureMatrix("keypad_mid_calculation") {
            MainKeypad(isExpandedKeypad = false, keys = MID_CALCULATION, callbacks = callbacks)
        }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.UNFOLDED)
    fun keypadOneHanded() =
        shots.captureMatrix("keypad_one_handed") {
            MainKeypad(isExpandedKeypad = false, keys = KeyAvailability(), callbacks = callbacks)
        }

    private companion object {
        // Just after a ")": the bracket can close again and = works, but a
        // decimal point or % would have nothing to attach to.
        val MID_CALCULATION = KeyAvailability(decimal = false, percent = false, closeParen = true, equals = true)
    }
}
