package com.eliormachlev.currencix.repository.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private val KEY = RateCacheKey.RatesLatest(1, "EUR", null)
private const val CONCURRENT_CALLERS = 8

class InFlightDedupeTest {
    @Test
    fun `concurrent callers for the same key coalesce onto one fetch`() =
        // The callers run on a pool of the test's own, so they really are
        // concurrent; closed once the test is done.
        Executors.newFixedThreadPool(CONCURRENT_CALLERS).asCoroutineDispatcher().use { pool ->
            concurrentCallersCoalesce(CoroutineScope(SupervisorJob() + pool))
        }

    private fun concurrentCallersCoalesce(scope: CoroutineScope) =
        runBlocking {
            val dedupe = InFlightDedupe<String>()
            val invocations = AtomicInteger(0)
            val producerRunning = CompletableDeferred<Unit>()
            val releaseProducer = CompletableDeferred<Unit>()

            val producer: suspend () -> Result<String> = {
                invocations.incrementAndGet()
                // Signal that the producer is inside the critical section
                // and hand back to the test. Because `releaseProducer` is
                // awaited on the next line, the producer will not complete
                // (and `invokeOnCompletion` will not clear the map entry)
                // until the test explicitly releases it — that guarantee
                // is what makes the arrival ordering below deterministic.
                producerRunning.complete(Unit)
                releaseProducer.await()
                Result.success("payload")
            }

            // First caller kicks off the fetch on the pool. We
            // then wait for the producer to actually be running so the
            // in-flight map is guaranteed to hold the shared Deferred.
            val first = scope.async { dedupe.get(KEY, scope, producer) }
            producerRunning.await()

            // With the producer parked, launch the remaining callers
            // UNDISPATCHED so each runs on this thread up to its first real
            // suspension (`deferred.await()` inside `get`). Every one of
            // them observes the existing entry and awaits the same Deferred
            // — no second producer is created.
            val rest =
                (2..CONCURRENT_CALLERS).map {
                    scope.async(start = CoroutineStart.UNDISPATCHED) {
                        dedupe.get(KEY, scope, producer)
                    }
                }

            releaseProducer.complete(Unit)
            val results = listOf(first.await()) + rest.awaitAll()

            assertEquals(1, invocations.get())
            results.forEach { assertEquals("payload", it.getOrNull()) }
        }
}
