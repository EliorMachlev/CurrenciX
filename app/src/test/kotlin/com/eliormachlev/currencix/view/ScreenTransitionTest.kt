package com.eliormachlev.currencix.view

import android.app.Activity
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins [BaseActivity]'s screen transition wiring on Android 14+, where it goes
 * through overrideActivityTransition (and so also drives the predictive-back
 * cross-activity animation). The animations themselves can only be judged on
 * a device; this guards against the wiring silently regressing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenTransitionTest {
    // Stands in for the launcher activity (MainActivity), which is too heavy
    // to boot here — the opt-out mechanism is what's under test.
    class LauncherLikeActivity : BaseActivity() {
        override val usesScreenTransition = false
    }

    @Test
    fun `secondary screens slide in over, and back off, the screen beneath`() {
        Robolectric.buildActivity(PreferenceActivity::class.java).setup().use { controller ->
            val shadow = shadowOf(controller.get())

            val open = shadow.getOverriddenActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN)
            assertEquals(R.anim.screen_enter, open?.enterAnim)
            assertEquals(R.anim.screen_hold, open?.exitAnim)

            val close = shadow.getOverriddenActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE)
            assertEquals(R.anim.screen_hold, close?.enterAnim)
            assertEquals(R.anim.screen_exit_pop, close?.exitAnim)
        }
    }

    @Test
    fun `an opted-out screen keeps the system transition`() {
        Robolectric.buildActivity(LauncherLikeActivity::class.java).setup().use { controller ->
            val shadow = shadowOf(controller.get())
            assertNull(shadow.getOverriddenActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN))
            assertNull(shadow.getOverriddenActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE))
        }
    }
}
