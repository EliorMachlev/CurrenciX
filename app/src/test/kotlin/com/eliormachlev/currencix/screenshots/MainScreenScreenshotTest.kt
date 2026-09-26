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
import com.eliormachlev.currencix.view.main.compose.MainScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// MainScreen is fully slot-based — no VM needed. We render placeholder
// display/keypad content to focus on the layout shell. Offline/historical
// state is now rendered inside the hero's RateFooter (see HeroCard tests),
// not by MainScreen, so there is nothing extra to snapshot here.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class MainScreenScreenshotTest {
    @Test fun mainScreenNormal() = captureMatrix("main_screen_normal") { MainScreenPreview() }
}

@Composable
private fun MainScreenPreview() {
    MainScreen(
        drawerState = rememberDrawerState(DrawerValue.Closed),
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
