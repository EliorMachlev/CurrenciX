package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.booleanPref
import com.eliormachlev.currencix.repository.persistence.prefStore

private const val KEY_APP_LOCK = "_appLock"
private const val KEY_PRIVATE_REQUESTS = "_privateRequests"
private const val KEY_BLOCK_SCREENSHOTS = "_blockScreenshots"
private const val KEY_CERTIFICATE_PINNING = "_certificatePinning"

// Where versions up to 1.23 kept the pins Re-pin took on the phone. Re-pin is
// gone (pins come only with the app), so what is left there is removed.
private val REMOVED_CERTIFICATE_PINS = stringSetPreferencesKey("_certificatePins")

/** Settings → Security & privacy: the app lock, screenshots, and requests that don't tell this phone apart. */
class PrivacySettings(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    /** Ask for the phone's biometrics or screen lock to open the app (see view/lock/AppLock). */
    val appLock: Pref<Boolean> = store.booleanPref(KEY_APP_LOCK, default = false)

    /** Keep the app out of screenshots, screen recordings and Recents (see view/lock/LockGate). */
    val blockScreenshots: Pref<Boolean> = store.booleanPref(KEY_BLOCK_SCREENSHOTS, default = false)

    /** Send every request the same way for every user (see util/PrivateRequests). */
    val privateRequests: Pref<Boolean> = store.booleanPref(KEY_PRIVATE_REQUESTS, default = false)

    /** Accept a rate server only through the certificate authorities pinned for it (see util/CertificatePinning). */
    val certificatePinning: Pref<Boolean> = store.booleanPref(KEY_CERTIFICATE_PINNING, default = true)

    /** Removes the pins an earlier version's Re-pin left on the phone; does nothing once they're gone. */
    fun removeTakenPins() = store.edit { remove(REMOVED_CERTIFICATE_PINS) }
}
