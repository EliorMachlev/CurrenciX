package com.eliormachlev.currencix.repository.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private val KEY = RateCacheKey.RatesLatest(1, "EUR", null)
private const val CONCURRENT_CALLERS = 8

class InFlightDedupeTest {
    @Test
    fun `concurrent callers for the same key coalesce onto one fetch`() =
        runBlocking {
            val dedupe = InFlightDedupe<String>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val invocations = AtomicInteger(0)
            val gate = CompletableDeferred<Unit>()

            // UNDISPATCHED so each caller runs on this thread up to its first
            // real suspension (`deferred.await()` inside `get`). Without it,
            // the callers are merely scheduled on Dispatchers.Default and
            // `gate.complete` below may fire before they arrive at the shared
            // deferred — the first producer would then complete instantly,
            // `invokeOnCompletion` would drop the map entry, and later callers
            // would spawn their own fetch. Serializing the arrivals here
            // guarantees the map contains the single shared deferred by the
            // time we release the gate.
            val callers =
                (1..CONCURRENT_CALLERS).map {
                    scope.async(start = CoroutineStart.UNDISPATCHED) {
                        dedupe.get(KEY, scope) {
                            invocations.incrementAndGet()
                            gate.await()
                            Result.success("payload")
                        }
                    }
                }

            // Release the single upstream fetch and confirm all callers see
            // the same result while only one fetch actually ran.
            gate.complete(Unit)
            val results = callers.awaitAll()

            assertEquals(1, invocations.get())
            results.forEach { assertEquals("payload", it.getOrNull()) }
        }
}
