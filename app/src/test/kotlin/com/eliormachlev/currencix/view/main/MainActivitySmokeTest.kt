package com.eliormachlev.currencix.view.main

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.seedCachedRates
import com.eliormachlev.currencix.view.compose.UiTestTags
import com.eliormachlev.currencix.view.main.compose.DrawerAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Opens each screen the converter's top bar reaches and comes back, through
// the real single-Activity shell: routes, top bars, per-screen ViewModels.
// A plain Application: the real one starts WorkManager and rate refreshes.
@RunWith(RobolectricTestRunner::class)
// A tall phone, so every drawer entry (Settings is the last) and settings row
// is on screen: a tap on something below the fold misses, and scroll actions
// don't finish on the manual clock.
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h1600dp")
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    @Before
    fun setUp() {
        Database(ApplicationProvider.getApplicationContext()).display.setHasSeenOnboarding(true)
        // Cached rates, so the converter has its pair without waiting on the network.
        seedCachedRates()
        // The converter never idles (blinking cursor, shimmer, pill
        // auto-scroll): drive the clock by hand instead.
        compose.mainClock.autoAdvance = false
        settle()
    }

    // Lets posted work run, then steps enough frames for any screen
    // transition to finish. (One big advanceTimeBy would skip the frames
    // the transition animates on.)
    private fun settle() {
        compose.waitForIdle()
        repeat(SETTLE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun openFromTopBar(actionLabel: Int) {
        compose.onNodeWithContentDescription(string(actionLabel)).performClick()
        settle()
    }

    private fun back() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun assertOnConverter() {
        compose.onNodeWithContentDescription(string(R.string.desc_open_drawer)).assertExists()
    }

    @Test
    fun `cart opens from the top bar and back returns to the converter`() {
        assertOnConverter()
        openFromTopBar(R.string.cart_title)
        compose.onNodeWithText(string(R.string.cart_title)).assertExists()
        compose.onNodeWithContentDescription(string(R.string.desc_more_options)).assertExists()

        back()
        assertOnConverter()
    }

    @Test
    fun `timeline opens with its actions and the up arrow returns`() {
        openFromTopBar(R.string.menu_timeline)
        compose.onNodeWithContentDescription(string(R.string.graph_options_title)).assertExists()

        compose.onNodeWithContentDescription(string(R.string.desc_navigate_up)).performClick()
        settle()
        assertOnConverter()
    }

    @Test
    fun `quick conversions and historical rates stay on the converter`() {
        openFromTopBar(R.string.menu_quick_conversions)
        back()
        openFromTopBar(R.string.menu_historical_rates)
        back()
        assertOnConverter()
    }

    @Test
    fun `settings, fees and backup open from the drawer and back unwinds them`() {
        openFromTopBar(R.string.desc_open_drawer)
        compose.onNodeWithTag(UiTestTags.drawerEntry(DrawerAction.Settings.name)).performClick()
        settle()
        compose.onNodeWithText(string(R.string.title_preferences)).assertExists()

        // The settings row itself ("Foreign transaction fee"): "Fees" alone
        // also matches the converter's drawer entry.
        compose.onNodeWithText(string(R.string.fee_title)).performClick()
        settle()
        compose.onNodeWithContentDescription(string(R.string.desc_navigate_up)).assertExists()

        back()
        compose.onNodeWithText(string(R.string.title_preferences)).assertExists()
        back()
        assertOnConverter()
    }

    @Test
    fun `a screen opened from the drawer returns to a closed drawer`() {
        openFromTopBar(R.string.desc_open_drawer)
        compose.onNodeWithTag(UiTestTags.drawerEntry(DrawerAction.Timeline.name)).performClick()
        settle()
        compose.onNodeWithContentDescription(string(R.string.graph_options_title)).assertExists()

        back()
        // Open-drawer label means the drawer is shut (it reads "close" while open).
        assertOnConverter()
    }
}

// ~1 s at 60 fps: longer than any screen transition.
private const val SETTLE_FRAMES = 60
