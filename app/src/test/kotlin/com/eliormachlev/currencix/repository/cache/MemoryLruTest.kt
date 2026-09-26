package com.eliormachlev.currencix.repository.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

// Distinct keys used across the assertions — hoisted so a rename catches every
// call site at once and there's no stringly-typed "USD" scattered inline.
private val KEY_A = RateCacheKey.RatesLatest(1, "EUR", null)
private val KEY_B = RateCacheKey.RatesLatest(2, "USD", null)
private val KEY_C = RateCacheKey.RatesLatest(3, "GBP", null)
private val KEY_D = RateCacheKey.RatesLatest(4, "JPY", null)

private val EPOCH_MILLIS = LocalDate.of(2026, 1, 1).toEpochDay() * 24L * 60L * 60L * 1000L

private fun entry(payload: String) = CachedEntry(payload, EPOCH_MILLIS)

class MemoryLruTest {
    @Test
    fun `evicts least recently used entry once capacity exceeded`() {
        val lru = MemoryLru<String>(maxEntries = 3)
        lru.put(KEY_A, entry("a"))
        lru.put(KEY_B, entry("b"))
        lru.put(KEY_C, entry("c"))

        // Touch A so it becomes most-recently-used; B is now the LRU tail.
        assertEquals("a", lru.get(KEY_A)?.value)

        // Overflow — B should evict.
        lru.put(KEY_D, entry("d"))

        assertEquals("a", lru.get(KEY_A)?.value)
        assertNull("B should have been evicted as least-recently-used", lru.get(KEY_B))
        assertEquals("c", lru.get(KEY_C)?.value)
        assertEquals("d", lru.get(KEY_D)?.value)
    }

    @Test
    fun `access order tracks reads not just writes`() {
        val lru = MemoryLru<String>(maxEntries = 2)
        lru.put(KEY_A, entry("a"))
        lru.put(KEY_B, entry("b"))

        // Reading A promotes it; B becomes the tail.
        lru.get(KEY_A)

        lru.put(KEY_C, entry("c"))

        assertNull(lru.get(KEY_B))
        assertEquals(listOf(KEY_A.stableId, KEY_C.stableId), lru.snapshotOrderedKeys())
    }
}
