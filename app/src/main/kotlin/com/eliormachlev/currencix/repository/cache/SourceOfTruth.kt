package com.eliormachlev.currencix.repository.cache

/**
 * Provider-agnostic persistent store for cached values.
 *
 * The interface deliberately avoids JSON, file, or Room primitives so a
 * future migration (e.g. #164 swapping the disk implementation for Room)
 * can drop in place without touching [RateCache] orchestration.
 *
 * Contract:
 *  - [read] returns `null` when the key is absent or unreadable (corrupted
 *    payload, decode failure). Never throws — callers treat `null` as a
 *    miss and fall through to the upstream [Fetcher].
 *  - [write] MUST be atomic w.r.t. concurrent readers of the same key —
 *    a reader that observes the entry sees either the prior value or the
 *    new one, never a partially-written intermediate.
 *  - [clear] deletes a single entry; missing entries are silently ignored.
 *  - [clearAll] purges the entire tier. Used for provider-swap invalidation
 *    and low-storage cleanup.
 */
interface SourceOfTruth<V : Any> {
    suspend fun read(key: RateCacheKey): CachedEntry<V>?

    suspend fun write(
        key: RateCacheKey,
        value: V,
    )

    suspend fun clear(key: RateCacheKey)

    suspend fun clearAll()
}

/**
 * A value read from a [SourceOfTruth] together with the wall-clock instant
 * at which it was written. Callers compare [writtenAtMillis] against a
 * per-provider TTL to decide whether to serve the cached value or refetch.
 *
 * `writtenAtMillis` is in `System.currentTimeMillis()` units so it survives
 * across process restarts (unlike `SystemClock.elapsedRealtime()`), which
 * is what makes a disk-tier TTL check meaningful.
 */
data class CachedEntry<V : Any>(
    val value: V,
    val writtenAtMillis: Long,
)
