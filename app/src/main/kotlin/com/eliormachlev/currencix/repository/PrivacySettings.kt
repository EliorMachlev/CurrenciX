package com.eliormachlev.currencix.repository

import android.content.Context
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.booleanPref
import com.eliormachlev.currencix.repository.persistence.prefStore

private const val KEY_APP_LOCK = "_appLock"
private const val KEY_PRIVATE_REQUESTS = "_privateRequests"
private const val KEY_BLOCK_SCREENSHOTS = "_blockScreenshots"

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
}
