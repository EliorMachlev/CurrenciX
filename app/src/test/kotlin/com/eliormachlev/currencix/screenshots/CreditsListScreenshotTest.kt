package com.eliormachlev.currencix.screenshots

import androidx.compose.ui.platform.LocalContext
import com.eliormachlev.currencix.view.preference.compose.CreditsList
import com.eliormachlev.currencix.view.preference.compose.creditsSections
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class CreditsListScreenshotTest {
    @Test fun creditsList() =
        captureMatrix("credits_list") {
            val context = LocalContext.current
            CreditsList(sections = creditsSections(context))
        }
}
