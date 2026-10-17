package com.eliormachlev.currencix.model

import com.eliormachlev.currencix.view.preference.compose.apiKeyTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    fun `the default fallback is the first free, reliable provider that isn't the main one`() {
        val candidates = order.filter { !it.needsApiKey && it.reliability.isReliable }
        assertEquals(candidates[0], ApiProvider.defaultFallback(ApiProvider.OPEN_EXCHANGERATES))
        assertEquals(candidates[1], ApiProvider.defaultFallback(candidates[0]))
        ApiProvider.entries.forEach { main ->
            val fallback = ApiProvider.defaultFallback(main)
            assertNotEquals(main, fallback)
            assertFalse(fallback.needsApiKey)
            assertTrue(fallback.reliability.isReliable)
        }
    }

    @Test
    fun `the unreliable providers are the ones the user was told about`() {
        val unreliable = ApiProvider.entries.filterNot { it.reliability.isReliable }.associateWith { it.reliability }
        assertEquals(
            mapOf(
                ApiProvider.NORGES_BANK to ProviderReliability.DOWNTIME,
                ApiProvider.INFOR_EURO to ProviderReliability.INACCURATE_DATA,
            ),
            unreliable,
        )
    }

    @Test
    fun `pickers hide unreliable providers unless asked, keeping the ones in use`() {
        assertEquals(order.filter { it.reliability.isReliable }, ApiProvider.listed(showUnreliable = false))
        assertEquals(order, ApiProvider.listed(showUnreliable = true))
        val kept = ApiProvider.listed(showUnreliable = false, keep = setOf(ApiProvider.NORGES_BANK))
        assertTrue(ApiProvider.NORGES_BANK in kept)
        assertFalse(ApiProvider.INFOR_EURO in kept)
        assertEquals(order.filter { it in kept }, kept)
    }

    @Test
    fun `every provider that needs a key has a key row to enter it, and only those`() {
        ApiProvider.entries.forEach { provider ->
            assertEquals(provider.name, provider.needsApiKey, provider.apiKeyTexts != null)
        }
    }
}
