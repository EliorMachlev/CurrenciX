package com.eliormachlev.currencix.repository

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// How long a provider that just failed is passed over for the fallback:
// long enough to ride out a short outage without waiting on it at every
// refresh, short enough that it's soon back in use once it recovers.
private val COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(10)

/**
 * Remembers which rate providers failed lately (process-wide, not saved),
 * so a refresh goes straight to the fallback instead of first waiting for
 * the one that's down. A provider is in cooldown for [COOLDOWN_MILLIS] after
 * its last failure, and out of it as soon as it answers again.
 */
class ProviderCooldown(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val failedAt = ConcurrentHashMap<Int, Long>()

    fun recordFailure(providerId: Int) {
        failedAt[providerId] = nowMillis()
    }

    fun recordSuccess(providerId: Int) {
        failedAt.remove(providerId)
    }

    fun isCoolingDown(providerId: Int): Boolean = failedAt[providerId]?.let { nowMillis() - it < COOLDOWN_MILLIS } == true

    companion object {
        /** The app's own: every repository shares what it learned. */
        val shared = ProviderCooldown()
    }
}
