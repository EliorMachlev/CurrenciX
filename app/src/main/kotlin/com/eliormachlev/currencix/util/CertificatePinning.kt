package com.eliormachlev.currencix.util

import okhttp3.CertificatePinner

// OkHttp's pattern for a host and every subdomain of it.
private const val SUBDOMAINS = "**."

/**
 * Settings → Security & privacy → Certificate pinning, as the HTTP client
 * reads it (kept in step with PrivacySettings by CurrenciesApplication). On,
 * a host's connection is accepted only when its verified chain holds one of
 * the keys built into the app for it ([BUILT_IN_PINS]); pins change only
 * with an app update. Off, any certificate the phone trusts is accepted, as
 * without pins.
 */
object CertificatePinning {
    private val pinned = pinnerFor(enabled = true)

    @Volatile
    var pinner: CertificatePinner = pinned
        private set

    /** Follows the setting; the HTTP clients pick up a new [pinner] at their next request. */
    fun update(enabled: Boolean) {
        pinner = if (enabled) pinned else CertificatePinner.DEFAULT
    }
}

/**
 * The pins in force: none when [enabled] is off, else the built-in ones.
 * Each host's pins hold for its subdomains too (`**.host`), as the allowed
 * hosts do (AllowedHostsInterceptor), so a redirect to one doesn't go
 * unpinned.
 */
internal fun pinnerFor(enabled: Boolean): CertificatePinner {
    if (!enabled) return CertificatePinner.DEFAULT
    return CertificatePinner
        .Builder()
        .apply {
            BUILT_IN_PINS.forEach { (host, pins) ->
                pins.forEach { pin -> add("$SUBDOMAINS$host", pin) }
            }
        }.build()
}
