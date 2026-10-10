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

            // First caller kicks off the fetch on Dispatchers.Default. We
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
