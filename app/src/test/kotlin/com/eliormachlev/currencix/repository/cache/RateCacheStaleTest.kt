package com.eliormachlev.currencix.repository.cache

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private val KEY = RateCacheKey.RatesLatest(1, "EUR", null)
private const val WRITTEN_AT = 0L
private const val PAST_TTL = RateCache.DEFAULT_TTL_MS + 1

class RateCacheStaleTest {
    private class MapStore(
        private val entries: MutableMap<RateCacheKey, CachedEntry<String>> = mutableMapOf(),
    ) : SourceOfTruth<String> {
        override suspend fun read(key: RateCacheKey) = entries[key]

        override suspend fun write(
            key: RateCacheKey,
            value: String,
        ) {
            entries[key] = CachedEntry(value, WRITTEN_AT)
        }

        override suspend fun clear(key: RateCacheKey) {
            entries.remove(key)
        }

        override suspend fun clearAll() = entries.clear()
    }

    // A cache holding an old entry whose upstream is [upstream]; time is past the entry's freshness.
    private fun cacheWithOldEntry(upstream: Result<String>): RateCache<RateCacheKey.RatesLatest, String> {
        val store = MapStore()
        runBlocking { store.write(KEY, "old") }
        return RateCache(
            sourceOfTruth = store,
            fetcher = { upstream },
            retryPolicy = RetryPolicy(maxAttempts = 1),
            nowMillis = { WRITTEN_AT + PAST_TTL },
        )
    }

    @Test
    fun `an old entry stands in when the upstream fails`() =
        runBlocking {
            val cache = cacheWithOldEntry(Result.failure(IOException("down")))
            assertEquals("old", cache.get(KEY).getOrNull())
        }

    @Test
    fun `an old entry is not an answer when stale values are not allowed`() =
        runBlocking {
            val cache = cacheWithOldEntry(Result.failure(IOException("down")))
            val result = cache.get(KEY, allowStale = false)
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun `a live answer is used whether or not stale values are allowed`() =
        runBlocking {
            val cache = cacheWithOldEntry(Result.success("live"))
            assertEquals("live", cache.get(KEY, allowStale = false).getOrNull())
        }

    @Test
    fun `serving an old entry is reported so the caller can tell it from a live answer`() =
        runBlocking {
            var stale = 0
            val old = cacheWithOldEntry(Result.failure(IOException("down")))
            assertEquals("old", old.get(KEY, onStale = { stale++ }).getOrNull())
            assertEquals(1, stale)

            val live = cacheWithOldEntry(Result.success("live"))
            assertEquals("live", live.get(KEY, onStale = { stale++ }).getOrNull())
            assertEquals(1, stale)
        }
}
