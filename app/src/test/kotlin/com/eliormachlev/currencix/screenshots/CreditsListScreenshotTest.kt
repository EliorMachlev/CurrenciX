package com.eliormachlev.currencix.screenshots

import android.app.Application
import androidx.compose.ui.platform.LocalContext
import com.eliormachlev.currencix.view.preference.compose.CreditsList
import com.eliormachlev.currencix.view.preference.compose.creditsSections
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class CreditsListScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    @Test fun creditsList() =
        shots.captureMatrix("credits_list") {
            val context = LocalContext.current
            CreditsList(sections = creditsSections(context))
        }
}
