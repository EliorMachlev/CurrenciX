package com.eliormachlev.currencix.util

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts saved data thrown away because it couldn't be read back (a rate or
 * a history that doesn't parse), per store. The data is fetched again, so
 * nothing breaks for the user; the count is logged (debug builds plant the
 * logger) so a store that keeps dropping entries, a sign of a write bug,
 * shows up instead of quietly costing requests.
 */
object DroppedCacheEntries {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    fun record(store: String) {
        val total = counts.computeIfAbsent(store) { AtomicInteger() }.incrementAndGet()
        Timber.tag(store).w("Dropped an unreadable cache entry (%d this run)", total)
    }

    fun count(store: String): Int = counts[store]?.get() ?: 0
}
