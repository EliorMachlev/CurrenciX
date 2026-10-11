package com.eliormachlev.currencix.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Click sounds are on until the user turns them off, and the switch sticks.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClickSoundsSettingTest {
    private val display = Database(ApplicationProvider.getApplicationContext()).display

    @Test
    fun `click sounds are on by default`() {
        assertTrue(display.clickSounds.get())
    }

    @Test
    fun `turning click sounds off sticks`() {
        display.clickSounds.set(false)
        try {
            assertFalse(display.clickSounds.get())
        } finally {
            // The store outlives a test: leave the default for the next one.
            display.clickSounds.set(true)
        }
    }
}
