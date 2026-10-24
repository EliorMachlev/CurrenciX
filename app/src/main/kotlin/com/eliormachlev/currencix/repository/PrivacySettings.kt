package com.eliormachlev.currencix.repository

import android.content.Context
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.booleanPref
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.repository.persistence.stringSetPref

private const val KEY_APP_LOCK = "_appLock"
private const val KEY_PRIVATE_REQUESTS = "_privateRequests"
private const val KEY_RECEIPT_LANGUAGES_ALLOWED = "_receiptLanguagesAllowed"
private const val KEY_RECEIPT_LANGUAGES_DECLINED = "_receiptLanguagesDeclined"

/**
 * Settings → Security & privacy: the app lock, requests that don't tell this
 * phone apart, and which receipt-reading languages the user agreed to download.
 */
class PrivacySettings(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    /** Ask for the phone's biometrics or screen lock to open the app (see view/lock/AppLock). */
    val appLock: Pref<Boolean> = store.booleanPref(KEY_APP_LOCK, default = false)

    /** Send every request the same way for every user (see util/PrivateRequests). */
    val privateRequests: Pref<Boolean> = store.booleanPref(KEY_PRIVATE_REQUESTS, default = false)

    /**
     * The receipt-reading languages (TessLanguage codes) the user said yes to
     * downloading, and those they said no to: a language is downloaded only
     * once it's in the first, and one in neither is asked about first.
     */
    val receiptLanguagesAllowed: Pref<Set<String>> = store.stringSetPref(KEY_RECEIPT_LANGUAGES_ALLOWED)
    val receiptLanguagesDeclined: Pref<Set<String>> = store.stringSetPref(KEY_RECEIPT_LANGUAGES_DECLINED)

    /** Records the user's answer about downloading [code]'s data (one set or the other, never both). */
    fun setReceiptLanguageAllowed(
        code: String,
        allowed: Boolean,
    ) {
        receiptLanguagesAllowed.update { if (allowed) it + code else it - code }
        receiptLanguagesDeclined.update { if (allowed) it - code else it + code }
    }
}
