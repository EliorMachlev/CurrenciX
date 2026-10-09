package com.eliormachlev.currencix.view.main

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.seedCachedRates
import com.eliormachlev.currencix.view.compose.UiTestTags
import com.eliormachlev.currencix.view.main.compose.DrawerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

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

    // A back swipe from the left edge, held partway: started and moved, not
    // let go. Returns where [node] sits while the swipe is held, for
    // comparing with where it sat before.
    private fun boundsDuringBackSwipe(
        dispatcher: OnBackPressedDispatcher,
        node: () -> DpRect,
    ): DpRect {
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(swipe(progress = 0f))
            dispatcher.dispatchOnBackProgressed(swipe(progress = HELD_SWIPE_PROGRESS))
        }
        settle()
        return node()
    }

    // Lets go of the held swipe, which commits the back.
    private fun releaseBackSwipe(dispatcher: OnBackPressedDispatcher) {
        compose.runOnIdle { dispatcher.onBackPressed() }
        settle()
    }

    private fun swipe(progress: Float) =
        BackEventCompat(touchX = 0f, touchY = 0f, progress = progress, swipeEdge = BackEventCompat.EDGE_LEFT)

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
    fun `settings describe both providers and how often each updates`() {
        openFromTopBar(R.string.desc_open_drawer)
        compose.onNodeWithTag(UiTestTags.drawerEntry(DrawerAction.Settings.name)).performClick()
        settle()
        // Row titles that start "About …" (a description may also contain the word).
        val about = string(R.string.api_about_title).substringBefore("%")
        val aboutTitle =
            SemanticsMatcher("text starts with \"$about\"") { node ->
                node.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .any { it.text.startsWith(about) }
            }
        compose.onAllNodes(aboutTitle).assertCountEquals(2)
        // …each with how often it updates.
        compose.onAllNodes(hasText(string(R.string.api_refreshPeriod_title))).assertCountEquals(2)
    }

    @Test
    fun `back closes an open drawer instead of leaving the app`() {
        openFromTopBar(R.string.desc_open_drawer)
        compose.onNodeWithContentDescription(string(R.string.desc_close_drawer)).assertExists()

        back()
        assertOnConverter()
        assertFalse("back left the app", compose.activity.isFinishing)
    }

    @Test
    fun `the open drawer slides in under the top bar, leaving its arrow uncovered`() {
        openFromTopBar(R.string.desc_open_drawer)
        val arrow = compose.onNodeWithContentDescription(string(R.string.desc_close_drawer)).getBoundsInRoot()
        val firstEntry = compose.onNodeWithTag(UiTestTags.drawerEntry(DrawerAction.Timeline.name)).getBoundsInRoot()
        assertTrue("the drawer covers the top bar", firstEntry.top >= arrow.bottom)

        // …and the arrow, still in reach, closes it.
        compose.onNodeWithContentDescription(string(R.string.desc_close_drawer)).performClick()
        settle()
        assertOnConverter()
    }

    @Test
    fun `a top bar shortcut used while the drawer is open returns to a closed drawer`() {
        openFromTopBar(R.string.desc_open_drawer)
        openFromTopBar(R.string.cart_title)
        compose.onNodeWithContentDescription(string(R.string.desc_more_options)).assertExists()

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

    // Back stays predictive, but nothing previews it: the swiped surface
    // holds still under the finger, and letting go plays the usual close.

    @Test
    fun `a back swipe doesn't shrink the drawer, and letting go closes it`() {
        openFromTopBar(R.string.desc_open_drawer)
        val entry = { compose.onNodeWithTag(UiTestTags.drawerEntry(DrawerAction.Timeline.name)).getBoundsInRoot() }
        val before = entry()

        val dispatcher = compose.activity.onBackPressedDispatcher
        assertEquals(before, boundsDuringBackSwipe(dispatcher, entry))

        releaseBackSwipe(dispatcher)
        assertOnConverter()
    }

    @Test
    fun `a back swipe doesn't shrink the screen, and letting go returns`() {
        openFromTopBar(R.string.cart_title)
        val title = { compose.onNodeWithText(string(R.string.cart_title)).getBoundsInRoot() }
        val before = title()

        val dispatcher = compose.activity.onBackPressedDispatcher
        assertEquals(before, boundsDuringBackSwipe(dispatcher, title))

        releaseBackSwipe(dispatcher)
        assertOnConverter()
    }

    @Test
    fun `a back swipe doesn't shrink a sheet, and letting go closes it`() {
        openFromTopBar(R.string.menu_quick_conversions)
        // The sheet starts sliding up only once its window is attached:
        // give it a second settle to finish.
        settle()
        // The sheet is its own window, with its own back dispatcher.
        val sheet = ShadowDialog.getLatestDialog() as ComponentDialog
        val title = { compose.onNodeWithText(string(R.string.quick_conversions_title).uppercase()).getBoundsInRoot() }
        val before = title()

        assertEquals(before, boundsDuringBackSwipe(sheet.onBackPressedDispatcher, title))

        releaseBackSwipe(sheet.onBackPressedDispatcher)
        assertFalse("back left the sheet open", sheet.isShowing)
        assertOnConverter()
    }
}

// ~1 s at 60 fps: longer than any screen transition.
private const val SETTLE_FRAMES = 60

// Far enough into a swipe that Material's preview would have shrunk things.
private const val HELD_SWIPE_PROGRESS = 0.5f
