package com.eliormachlev.currencix.repository

import android.app.Application
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.prefStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pins come only with the app: what an earlier version's Re-pin stored goes.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PrivacySettingsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = PersistenceKey.APP.prefStore(app)

    @Test
    fun `pins an earlier Re-pin stored are removed, and the rest is kept`() {
        runBlocking {
            prefs.editAndAwait {
                this[TAKEN_PINS] = setOf("api.frankfurter.dev sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
                this[OTHER] = setOf("kept")
            }
        }
        PrivacySettings(app).removeTakenPins()
        assertNull(prefs.snapshot()[TAKEN_PINS])
        assertEquals(setOf("kept"), prefs.snapshot()[OTHER])
    }

    private companion object {
        val TAKEN_PINS = stringSetPreferencesKey("_certificatePins")
        val OTHER = stringSetPreferencesKey("_privacySettingsTestProbe")
    }
}
