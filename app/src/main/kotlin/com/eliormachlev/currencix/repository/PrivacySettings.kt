package com.eliormachlev.currencix.repository

import android.content.Context
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.booleanPref
import com.eliormachlev.currencix.repository.persistence.prefStore

private const val KEY_APP_LOCK = "_appLock"
private const val KEY_PRIVATE_REQUESTS = "_privateRequests"

/** Settings → Security & privacy: the app lock, and requests that don't tell this phone apart. */
class PrivacySettings(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    /** Ask for the phone's biometrics or screen lock to open the app (see view/lock/AppLock). */
    val appLock: Pref<Boolean> = store.booleanPref(KEY_APP_LOCK, default = false)

    /** Send every request the same way for every user (see util/PrivateRequests). */
    val privateRequests: Pref<Boolean> = store.booleanPref(KEY_PRIVATE_REQUESTS, default = false)
}
