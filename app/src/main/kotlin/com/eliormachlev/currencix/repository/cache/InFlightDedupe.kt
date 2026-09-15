package com.eliormachlev.currencix.repository.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coalesces concurrent callers waiting on the same key onto a single upstream
 * fetch. First caller creates a [Deferred]; subsequent callers `await()` the
 * same instance instead of triggering a duplicate network round-trip.
 *
 * ### Why a `Mutex` around a plain `Map`
 * The map is written by whichever coroutine wins the race to insert a key
 * and read by every subsequent caller; without serialization two callers
 * could each see an empty map and both spawn their own fetch. A short
 * critical section keyed on [Mutex] is simpler (and correct on both JVM
 * and native) than a concurrent-collection approach that would still need
 * atomic get-or-compute semantics with suspending initialization.
 *
 * ### Cleanup
 * Entries are removed via `invokeOnCompletion` — so a completed / cancelled
 * fetch never sticks around to short-circuit the next call. The map thus
 * only ever grows to the count of currently-in-flight keys (typically
 * ≤ 1 in this app).
 */
internal class InFlightDedupe<V : Any> {
    private val mutex = Mutex()
    private val inflight = mutableMapOf<String, Deferred<Result<V>>>()

    /**
     * Returns the [Result] of the single upstream fetch identified by [key].
     * If a fetch is already running, the caller awaits its result; otherwise
     * [producer] is invoked inside [scope] and its outcome is shared.
     */
    suspend fun get(
        key: RateCacheKey,
        scope: CoroutineScope,
        producer: suspend () -> Result<V>,
    ): Result<V> {
        val deferred =
            mutex.withLock {
                inflight[key.stableId]
                    ?: scope
                        .async { producer() }
                        .also { newDeferred ->
                            inflight[key.stableId] = newDeferred
                            // Detach in the completion handler; guarded by the
                            // same mutex so a follow-up caller sees a clean map
                            // once cleanup runs.
                            newDeferred.invokeOnCompletion {
                                // No suspension allowed inside invokeOnCompletion,
                                // so drop the entry with a non-blocking remove; a
                                // rare race where a later caller observes the
                                // completed Deferred just awaits its cached
                                // result — still one upstream fetch.
                                inflight.remove(key.stableId)
                            }
                        }
            }
        return deferred.await()
    }
}
