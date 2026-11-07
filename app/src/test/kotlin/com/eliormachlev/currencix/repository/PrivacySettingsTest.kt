package com.eliormachlev.currencix.repository

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
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

// Settings an earlier version kept and this one no longer has are removed:
// the Certificate pinning switch and the pins Re-pin took.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PrivacySettingsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = PersistenceKey.APP.prefStore(app)

    @Test
    fun `retired settings are removed, and the rest is kept`() {
        runBlocking {
            prefs.editAndAwait {
                this[TAKEN_PINS] = setOf("api.frankfurter.dev sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
                this[PINNING_SWITCH] = false
                this[OTHER] = setOf("kept")
            }
        }
        PrivacySettings(app).removeRetiredSettings()
        assertNull(prefs.snapshot()[TAKEN_PINS])
        assertNull(prefs.snapshot()[PINNING_SWITCH])
        assertEquals(setOf("kept"), prefs.snapshot()[OTHER])
    }

    private companion object {
        val TAKEN_PINS = stringSetPreferencesKey("_certificatePins")
        val PINNING_SWITCH = booleanPreferencesKey("_certificatePinning")
        val OTHER = stringSetPreferencesKey("_privacySettingsTestProbe")
    }
}
