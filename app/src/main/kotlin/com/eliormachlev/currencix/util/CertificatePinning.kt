package com.eliormachlev.currencix.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.Handshake
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.security.cert.X509Certificate

private const val TAG = "CertificatePinning"

// OkHttp's pattern for a host and every subdomain of it.
private const val SUBDOMAINS = "**."

/**
 * Settings → Security & privacy → Certificate pinning, as the HTTP client
 * reads it (kept in step with PrivacySettings by CurrenciesApplication). On,
 * a host's connection is accepted only when its verified chain holds one of
 * the keys pinned for it: the built-in ones ([BUILT_IN_PINS]), or the ones
 * taken on this phone ([update]'s userPins), which replace them for that
 * host. Off, any certificate the phone trusts is accepted, as without pins.
 */
object CertificatePinning {
    @Volatile
    var pinner: CertificatePinner = pinnerFor(enabled = true, userPins = emptyMap())
        private set

    @Volatile
    private var settings: Pair<Boolean, Map<String, Set<String>>> = true to emptyMap()

    /** Follows the settings; the HTTP clients pick up a new [pinner] at their next request. */
    fun update(
        enabled: Boolean,
        userPins: Map<String, Set<String>>,
    ) {
        val next = enabled to userPins
        if (next == settings) return
        settings = next
        pinner = pinnerFor(enabled, userPins)
    }
}

/**
 * The pins in force: none when [enabled] is off, else the built-in ones with
 * [userPins] replacing theirs. Each host's pins hold for its subdomains too
 * (`**.host`), as the allowed hosts do (AllowedHostsInterceptor), so a
 * redirect to one doesn't go unpinned.
 */
internal fun pinnerFor(
    enabled: Boolean,
    userPins: Map<String, Set<String>>,
): CertificatePinner {
    if (!enabled) return CertificatePinner.DEFAULT
    return CertificatePinner
        .Builder()
        .apply {
            (BUILT_IN_PINS + userPins).forEach { (host, pins) ->
                pins.forEach { pin -> add("$SUBDOMAINS$host", pin) }
            }
        }.build()
}

/**
 * The pins of the certificate authorities in a connection's verified chain:
 * every certificate but the server's own (the first), which changes at each
 * renewal. OkHttp hands the chain over as the phone verified it, root
 * included, not as the server sent it.
 */
internal fun Handshake.authorityPins(): Set<String> =
    peerCertificates.drop(1).filterIsInstance<X509Certificate>().mapTo(sortedSetOf(), CertificatePinner::pin)

/**
 * Each of [hosts]' pins as it serves them now (Settings → Re-pin), from a
 * connection made without pins: any chain the phone trusts. A host that
 * can't be reached is left out, so it keeps the pins it had.
 */
internal suspend fun currentPins(
    hosts: Set<String>,
    dispatcher: CoroutineDispatcher = AppDispatchers.production.io,
): Map<String, Set<String>> =
    withContext(dispatcher) {
        val client = HttpClientProvider.unpinnedClient()
        hosts
            .map { host -> async { host to client.authorityPinsOf(host) } }
            .awaitAll()
            .mapNotNull { (host, pins) -> pins?.takeIf { it.isNotEmpty() }?.let { host to it } }
            .toMap()
    }

// Only the handshake matters: any answer, even an error page, comes after it.
private fun OkHttpClient.authorityPinsOf(host: String): Set<String>? =
    runCatching {
        val request =
            Request
                .Builder()
                .url("https://$host/")
                .head()
                .build()
        newCall(request).execute().use { it.handshake?.authorityPins() }
    }.onFailure { Timber.tag(TAG).w(it, "No certificate chain from %s", host) }
        .getOrNull()
