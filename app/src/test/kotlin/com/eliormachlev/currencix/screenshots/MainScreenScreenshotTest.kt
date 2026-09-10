package com.eliormachlev.currencix.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.view.main.compose.BannerContent
import com.eliormachlev.currencix.view.main.compose.BannerKind
import com.eliormachlev.currencix.view.main.compose.MainScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// MainScreen is fully slot-based — no VM needed. We render placeholder
// display/keypad content so we can focus on the banner + layout across
// three canonical states: NORMAL (no banner), OFFLINE, HISTORICAL.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class MainScreenScreenshotTest {
    @Test fun mainScreenNormal() =
        captureMatrix("main_screen_normal") { MainScreenPreview(banner = null) }

    @Test fun mainScreenOffline() =
        captureMatrix("main_screen_offline") {
            MainScreenPreview(banner = BannerContent(BannerKind.Offline, "Offline — showing cached rates"))
        }

    @Test fun mainScreenHistorical() =
        captureMatrix("main_screen_historical") {
            MainScreenPreview(banner = BannerContent(BannerKind.Historical, "Rates for Jan 5, 2024"))
        }
}

@Composable
private fun MainScreenPreview(banner: BannerContent?) {
    MainScreen(
        drawerState = rememberDrawerState(DrawerValue.Closed),
        banner = banner,
        isRefreshing = false,
        onRefresh = {},
        isRefreshDrawerEnabled = true,
        onDrawerItem = {},
        foldingFeature = null,
        displayContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Text("Display slot", Modifier.padding(16.dp))
            }
        },
        keypadContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Text("Keypad slot", Modifier.padding(16.dp))
            }
        },
    )
}
