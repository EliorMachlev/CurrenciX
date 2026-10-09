package com.eliormachlev.currencix.view.main

import android.app.Application
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.seedCachedRates
import com.eliormachlev.currencix.view.compose.UiTestTags
import com.eliormachlev.currencix.view.main.compose.DrawerAction
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The converter screen in Hebrew: the top bar and the drawer follow the
// language (hamburger on the right, shortcuts on the left, drawer from the
// right), while the converter itself keeps its left-to-right geometry.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "iw-w411dp-h1600dp")
class ConverterRtlTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    @Before
    fun setUp() {
        Database(ApplicationProvider.getApplicationContext()).display.setHasSeenOnboarding(true)
        seedCachedRates()
        compose.mainClock.autoAdvance = false
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        repeat(SETTLE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun bounds(description: Int) = compose.onNodeWithContentDescription(string(description)).getBoundsInRoot()

    private fun DpRect.centerX() = (left + right) / 2

    @Test
    fun `the hamburger sits on the right, after the shortcuts`() {
        val hamburger = bounds(R.string.desc_open_drawer)
        val cart = bounds(R.string.cart_title)
        val screen = compose.onRoot().getBoundsInRoot()
        assertTrue("hamburger at $hamburger", hamburger.centerX() > screen.centerX())
        assertTrue("hamburger $hamburger left of the cart $cart", hamburger.left > cart.right)
    }

    @Test
    fun `the drawer opens on the right`() {
        compose.onNodeWithContentDescription(string(R.string.desc_open_drawer)).performClick()
        settle()
        val entry = compose.onNodeWithTag(UiTestTags.drawerEntry(DrawerAction.Settings.name)).getBoundsInRoot()
        val screen = compose.onRoot().getBoundsInRoot()
        assertTrue("drawer entry at $entry in $screen", entry.right > screen.right - EDGE_SLACK)
        assertTrue("drawer entry at $entry in $screen", entry.left > screen.left + EDGE_SLACK)
    }

    @Test
    fun `the keypad stays left to right`() {
        val seven = compose.onNodeWithTag(UiTestTags.key("7")).getBoundsInRoot()
        val nine = compose.onNodeWithTag(UiTestTags.key("9")).getBoundsInRoot()
        assertTrue("7 at $seven, 9 at $nine", seven.right <= nine.left)
    }
}

// ~1 s at 60 fps: longer than the drawer's slide.
private const val SETTLE_FRAMES = 60

// The drawer sheet hugs the right edge; it doesn't reach the left one.
private val EDGE_SLACK = 48.dp
