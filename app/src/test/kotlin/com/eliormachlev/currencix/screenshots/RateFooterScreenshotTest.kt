package com.eliormachlev.currencix.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.view.main.compose.BannerContent
import com.eliormachlev.currencix.view.main.compose.BannerKind
import com.eliormachlev.currencix.view.main.compose.FOOTER_RATES
import com.eliormachlev.currencix.view.main.compose.FooterState
import com.eliormachlev.currencix.view.main.compose.OFFLINE_BANNER
import com.eliormachlev.currencix.view.main.compose.RateFooter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The converter card's rate line: online (when and from whom), offline and
// on the fallback (the status pill in that place, the row the same height).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class RateFooterScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    @Test fun rateFooterStates() =
        shots.captureMatrix("rate_footer_states") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(
                    null,
                    OFFLINE_BANNER,
                    BannerContent(BannerKind.Fallback, "Bank of Israel unavailable • Using Frankfurter.app", explanation = "…"),
                ).forEach { banner ->
                    RateFooter(
                        Currency.USD,
                        Currency.ILS,
                        FooterState(FOOTER_RATES, "dd/MM/yy", banner),
                        onProviderClick = {},
                        onBannerClick = {},
                    )
                }
            }
        }
}
