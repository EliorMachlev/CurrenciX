package com.eliormachlev.currencix.repository

import android.content.Context
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.booleanPref
import com.eliormachlev.currencix.repository.persistence.mapped
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.repository.persistence.stringSetPref

private const val KEY_APP_LOCK = "_appLock"
private const val KEY_PRIVATE_REQUESTS = "_privateRequests"
private const val KEY_BLOCK_SCREENSHOTS = "_blockScreenshots"
private const val KEY_CERTIFICATE_PINNING = "_certificatePinning"
private const val KEY_CERTIFICATE_PINS = "_certificatePins"

// One stored entry per pin: "<host> <pin>" (neither holds a space).
private const val PIN_SEPARATOR = ' '
private const val PIN_PREFIX = "sha256/"

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

    /** The pins taken on this phone (Re-pin), by host; each replaces that host's built-in pins. */
    val certificatePins: Pref<Map<String, Set<String>>> =
        store.stringSetPref(KEY_CERTIFICATE_PINS).mapped(::decodePins, ::encodePins)
}

internal fun encodePins(pins: Map<String, Set<String>>): Set<String> =
    pins.flatMapTo(HashSet()) { (host, hostPins) -> hostPins.map { "$host$PIN_SEPARATOR$it" } }

// Entries that aren't "<host> sha256/…" are skipped.
internal fun decodePins(entries: Set<String>): Map<String, Set<String>> =
    entries
        .mapNotNull { entry ->
            val host = entry.substringBefore(PIN_SEPARATOR, missingDelimiterValue = "")
            val pin = entry.substringAfter(PIN_SEPARATOR, missingDelimiterValue = "")
            if (host.isNotEmpty() && pin.startsWith(PIN_PREFIX) && pin.length > PIN_PREFIX.length) host to pin else null
        }.groupBy({ it.first }, { it.second })
        .mapValues { (_, pins) -> pins.toSet() }
