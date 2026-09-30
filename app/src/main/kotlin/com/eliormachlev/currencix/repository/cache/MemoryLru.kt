package com.eliormachlev.currencix.repository.cache

/**
 * Small process-lifetime LRU on top of [LinkedHashMap]'s `accessOrder` mode.
 *
 * Intentionally minimal: `get` returns the cached [CachedEntry] and bumps
 * recency; `put` evicts the least-recently-used tail once [maxEntries] is
 * exceeded. No TTL / expiration logic — that stays in [RateCache] so a
 * single freshness policy governs both memory and disk tiers.
 *
 * Thread-safety: every mutator/reader synchronizes on `this`. The map is
 * small (default 32 entries) and access patterns are I/O-adjacent, so a
 * plain intrinsic lock is more than fast enough and avoids pulling in
 * `androidx.collection.LruCache` (which is Android-only and would break
 * pure-JVM unit tests).
 */
internal class MemoryLru<V : Any>(
    private val maxEntries: Int,
) {
    private val map =
        object : LinkedHashMap<String, CachedEntry<V>>(
            // initialCapacity =
            maxEntries,
            // loadFactor =
            LOAD_FACTOR,
            // accessOrder =
            true,
        ) {
            override fun removeEldestEntry(eldest: Map.Entry<String, CachedEntry<V>>?): Boolean = size > maxEntries
        }

    fun get(key: RateCacheKey): CachedEntry<V>? = synchronized(this) { map[key.stableId] }

    fun put(
        key: RateCacheKey,
        entry: CachedEntry<V>,
    ) {
        synchronized(this) { map[key.stableId] = entry }
    }

    fun remove(key: RateCacheKey) {
        synchronized(this) { map.remove(key.stableId) }
    }

    fun clear() {
        synchronized(this) { map.clear() }
    }

    // Test-only: LRU insertion order snapshot. Not part of the public
    // contract; kept `internal` so the eviction test can assert it.
    internal fun snapshotOrderedKeys(): List<String> = synchronized(this) { map.keys.toList() }

    private companion object {
        // Standard LinkedHashMap default; expressed as a constant to keep
        // the constructor call self-documenting under the detekt MagicNumber
        // rule.
        const val LOAD_FACTOR = 0.75f
    }
}
