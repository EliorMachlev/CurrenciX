package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.AppTheme
import com.eliormachlev.currencix.model.Language
import com.eliormachlev.currencix.repository.Database
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The settings that do more than store a value: the theme (does the screen
// need recreating?) and the app language.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PreferenceSettingsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val settings = PreferenceSettings(app)

    @Before
    fun setUp() = restoreDefaults()

    @After
    fun tearDown() = restoreDefaults()

    // The stores outlive a test, so each starts and ends on the defaults.
    private fun restoreDefaults() {
        Database(app).display.theme.set(AppTheme.DEFAULT)
        settings.setLanguage(Language.SYSTEM)
    }

    @Test
    fun `a theme with another night mode is applied without recreating the screen`() {
        settings.setTheme(AppTheme.LIGHT)

        assertFalse(settings.setTheme(AppTheme.DARK))
        assertEquals(AppTheme.DARK, Database(app).display.theme.get())
    }

    @Test
    fun `only the pure-black switch in dark mode needs the screen recreated`() {
        settings.setTheme(AppTheme.DARK)

        assertTrue(settings.setTheme(AppTheme.OLED))
        assertTrue(settings.setTheme(AppTheme.DARK))
    }

    @Test
    fun `the same pure-black switch while the theme is light needs no recreation`() {
        settings.setTheme(AppTheme.LIGHT)

        assertFalse(settings.setTheme(AppTheme.LIGHT))
    }

    @Test
    fun `the language follows the phone until one is picked`() {
        assertEquals(Language.SYSTEM, settings.getLanguage())
    }

    @Test
    fun `a picked language is read back and the phone's can be restored`() {
        settings.setLanguage(Language.DE)
        assertEquals(Language.DE, settings.getLanguage())

        settings.setLanguage(Language.SYSTEM)
        assertEquals(Language.SYSTEM, settings.getLanguage())
    }
}
