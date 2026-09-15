package com.eliormachlev.currencix.repository.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

/**
 * Top-level orchestrator inspired by Store5's Fetcher / SourceOfTruth /
 * Converter triad, sized for this codebase (~300–400 lines total across the
 * `cache` package — no Store5 dependency).
 *
 * ### Read path
 * `get(key)` walks memory → disk → upstream:
 *  1. Memory tier hit within [ttlFor] → return immediately, no network.
 *  2. Disk tier hit within [ttlFor] → hydrate memory tier, return.
 *  3. Cache miss → dedupe against any in-flight fetch; execute with
 *     [RetryPolicy] backoff on transient failures.
 *
 * ### Write path
 * On successful upstream fetch the value is written to disk (atomically,
 * see [DiskJsonStore]) *before* memory, so a crash between the two tiers
 * still yields a coherent cache on next launch.
 *
 * ### Layering with OkHttp Cache
 * The shared `OkHttpClient` (see `HttpClientProvider`) keeps a 5 MiB
 * disk cache of raw HTTP bytes plus a per-provider `Cache-Control` rewrite
 * interceptor. This layer sits *on top* of that — different tiers, different
 * jobs: OkHttp caches transport-level responses, RateCache caches parsed
 * `ExchangeRates` / `Timeline` domain objects. Keeping both means we get
 * conditional GETs / `If-Modified-Since` semantics from OkHttp *and* skip
 * the parse cost when the domain object is still fresh.
 *
 * ### Concurrency / lifetime
 * A single [SupervisorJob]-scoped coroutine hosts every in-flight fetch —
 * so a fetch cancelled by its caller doesn't tear down siblings. The cache
 * has process lifetime; there's no `close()`. Consumers that need a Flow
 * (e.g. ViewModels via `stateInWhileSubscribed`) can wrap `get()` at the
 * boundary — we deliberately keep the internal surface suspend-only.
 *
 * ### #164 swap-out
 * [SourceOfTruth] is provider-agnostic and never leaks JSON details, so a
 * future Room-backed implementation can be dropped in place of
 * [DiskJsonStore] with no changes to this class.
 */
internal class RateCache<K : RateCacheKey, V : Any>(
    private val sourceOfTruth: SourceOfTruth<V>,
    private val fetcher: Fetcher<K, V>,
    private val memoryLru: MemoryLru<V> = MemoryLru(DEFAULT_MEMORY_ENTRIES),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val dedupe: InFlightDedupe<V> = InFlightDedupe(),
    // Wall-clock supplier — inlined so tests can pin time and assert
    // freshness checks against fixed instants.
    private val nowMillis: () -> Long = System::currentTimeMillis,
    // Coroutine scope for in-flight fetches. SupervisorJob so a cancelled
    // caller doesn't cancel unrelated in-flight fetches sharing the scope.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    // Per-key TTL policy. Defaulted so callers that don't need per-key
    // variance can pass a single duration once at construction.
    private val ttlFor: (K) -> Long = { DEFAULT_TTL_MS },
) {
    /**
     * Returns the freshest available value for [key]:
     *   memory (fresh) → disk (fresh) → upstream.
     *
     * On upstream failure with any stale-but-present cached entry, the
     * stale value is returned so the UI can still render something rather
     * than error out — matches the existing "preserve last-known-good"
     * behaviour in `ExchangeRatesRepository.postError`.
     */
    suspend fun get(key: K): Result<V> {
        readFresh(key)?.let { return Result.success(it) }
        return dedupe
            .get(key, scope) { fetchWithRetry(key) }
            .recoverCatching { error ->
                staleFallback(key) ?: throw error
            }
    }

    /**
     * Forces an upstream refetch, ignoring cached values but still populating
     * both tiers with the result. Used by explicit-refresh paths (swipe-to-
     * refresh, WorkManager cadence in #151) where the user's intent is
     * "give me the latest, even if cache says fresh".
     */
    suspend fun refresh(key: K): Result<V> = dedupe.get(key, scope) { fetchWithRetry(key) }

    suspend fun invalidate(key: K) {
        memoryLru.remove(key)
        sourceOfTruth.clear(key)
    }

    suspend fun invalidateAll() {
        memoryLru.clear()
        sourceOfTruth.clearAll()
    }

    private suspend fun readFresh(key: K): V? {
        val ttl = ttlFor(key)
        val memHit = memoryLru.get(key)?.takeIf { isFresh(it, ttl) }
        if (memHit != null) return memHit.value
        val diskEntry = sourceOfTruth.read(key)?.takeIf { isFresh(it, ttl) } ?: return null
        memoryLru.put(key, diskEntry)
        return diskEntry.value
    }

    private suspend fun staleFallback(key: K): V? {
        memoryLru.get(key)?.let { return it.value }
        return sourceOfTruth.read(key)?.also { memoryLru.put(key, it) }?.value
    }

    private suspend fun fetchWithRetry(key: K): Result<V> =
        retryPolicy
            .execute { fetcher.fetch(key) }
            .onSuccess { value ->
                // Disk before memory so a crash between the two tiers still
                // leaves a coherent cache on next launch — memory is
                // process-lifetime and always rebuildable from disk.
                sourceOfTruth.write(key, value)
                memoryLru.put(key, CachedEntry(value, nowMillis()))
            }

    private fun isFresh(
        entry: CachedEntry<V>,
        ttlMs: Long,
    ): Boolean = (nowMillis() - entry.writtenAtMillis) < ttlMs

    companion object {
        // Small enough to be a rounding error on any device we ship to, big
        // enough to hold every provider × recent-window combination the UI
        // realistically touches (7 providers × a handful of pairs).
        const val DEFAULT_MEMORY_ENTRIES = 32

        // 15 minutes — chosen to match the longest cadence any hot-path
        // caller expects to poll at, while staying well under the shortest
        // OkHttp cache TTL (1 h for rewritten providers) so this layer
        // never serves data staler than the HTTP tier would.
        const val DEFAULT_TTL_MS = 15L * 60L * 1000L
    }
}
