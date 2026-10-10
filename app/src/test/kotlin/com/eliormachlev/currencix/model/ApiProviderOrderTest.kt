package com.eliormachlev.currencix.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApiProviderOrderTest {
    private val order = ApiProvider.pickerOrder

    @Test
    fun `free providers come before the ones that need an API key`() {
        val firstKeyed = order.indexOfFirst { it.needsApiKey }
        assertFalse(order.drop(firstKeyed).any { !it.needsApiKey })
    }

    @Test
    fun `within a group, more frequent updates come first`() {
        order.groupBy { it.needsApiKey }.values.forEach { group ->
            assertEquals(group.sortedBy { it.cadence }, group)
        }
    }

    @Test
    fun `the picker lists every provider once`() {
        assertEquals(ApiProvider.entries.toSet(), order.toSet())
        assertEquals(ApiProvider.entries.size, order.size)
    }

    @Test
    fun `the default fallback is the first free provider that isn't the main one`() {
        val first = order.first { !it.needsApiKey }
        val second = order.filter { !it.needsApiKey }[1]
        assertEquals(first, ApiProvider.defaultFallback(ApiProvider.OPEN_EXCHANGERATES))
        assertEquals(second, ApiProvider.defaultFallback(first))
        ApiProvider.entries.forEach { main ->
            val fallback = ApiProvider.defaultFallback(main)
            assertNotEquals(main, fallback)
            assertFalse(fallback.needsApiKey)
        }
    }
}
