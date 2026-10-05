package com.eliormachlev.currencix.view.main

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.screenshots.SCREENSHOT_DIR
import com.eliormachlev.currencix.util.seedCachedRates
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// On a tablet-wide window the timeline and the cart open beside the
// converter (TwoPaneScene) instead of over it, and replace each other there.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w1100dp-h760dp-land-xhdpi")
class TwoPaneSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    @Before
    fun setUp() {
        Database(ApplicationProvider.getApplicationContext()).setHasSeenOnboarding(true)
        // Cached rates, so the converter has its pair without waiting on the network.
        seedCachedRates()
        compose.mainClock.autoAdvance = false
        settle()
    }

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

    private fun assertConverterShown() = compose.onNodeWithContentDescription(string(R.string.desc_open_drawer)).assertExists()

    private fun assertTimelineShown() = compose.onNodeWithContentDescription(string(R.string.graph_options_title)).assertExists()

    @Test
    fun `timeline opens beside the converter and back closes only the pane`() {
        openFromTopBar(R.string.menu_timeline)
        assertConverterShown()
        assertTimelineShown()
        compose.onRoot().captureRoboImage("$SCREENSHOT_DIR/two_pane_timeline.png")

        back()
        assertConverterShown()
        compose.onNodeWithContentDescription(string(R.string.graph_options_title)).assertDoesNotExist()
    }

    @Test
    fun `the cart takes the timeline's pane rather than stacking on it`() {
        openFromTopBar(R.string.menu_timeline)
        openFromTopBar(R.string.cart_title)
        assertConverterShown()
        compose.onNodeWithText(string(R.string.cart_add_item)).assertExists()
        compose.onNodeWithContentDescription(string(R.string.graph_options_title)).assertDoesNotExist()
        compose.onRoot().captureRoboImage("$SCREENSHOT_DIR/two_pane_cart.png")

        // One back: the cart replaced the timeline, so it's the converter alone.
        back()
        assertConverterShown()
        compose.onNodeWithText(string(R.string.cart_add_item)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.graph_options_title)).assertDoesNotExist()
    }
}

private const val SETTLE_FRAMES = 60
