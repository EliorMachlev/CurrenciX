package com.eliormachlev.currencix.view.main.compose

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.util.registerActivityRule
import com.eliormachlev.currencix.view.compose.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

internal val FOOTER_RATES =
    ExchangeRates(
        success = true,
        error = null,
        base = Currency.EUR,
        date = LocalDate.of(2026, 10, 16),
        time = LocalTime.of(16, 0),
        rates = listOf(Rate(Currency.EUR, BigDecimal.ONE), Rate(Currency.USD, BigDecimal("1.08")), Rate(Currency.ILS, BigDecimal("3.336"))),
        provider = ApiProvider.BANK_OF_ISRAEL,
    )

internal val OFFLINE_BANNER = BannerContent(BannerKind.Offline, "Offline • rates from 16/10/26")

/**
 * Going offline (or coming back) puts the status pill in the rate row, in the
 * timestamp's place, and must not change the card's height: a taller card
 * pushed the recent pairs under the keypad.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RateFooterHeightTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    @Test
    fun `the footer is as tall with the status pill as without it`() = assertSameHeight(fontScale = 1f)

    @Test
    fun `and at a large font size`() = assertSameHeight(fontScale = LARGE_FONT_SCALE)

    private fun assertSameHeight(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme(dynamicColor = false, clickSounds = false) {
                    Column(Modifier.width(360.dp)) {
                        listOf("online" to null, "offline" to OFFLINE_BANNER).forEach { (tag, banner) ->
                            Column(Modifier.testTag(tag)) {
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
            }
        }
        val online = compose.onNodeWithTag("online").getBoundsInRoot().height
        val offline = compose.onNodeWithTag("offline").getBoundsInRoot().height
        assertEquals(online, offline)
    }

    private companion object {
        const val LARGE_FONT_SCALE = 1.5f
    }
}
