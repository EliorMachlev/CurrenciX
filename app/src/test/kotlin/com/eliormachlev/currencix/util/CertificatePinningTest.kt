package com.eliormachlev.currencix.util

import okhttp3.CertificatePinner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// Which keys each host must chain to, and how the Settings switch changes them.
class CertificatePinningTest {
    @After
    fun backToDefaults() = CertificatePinning.update(enabled = true)

    @Test
    fun `every host the app may reach has built-in pins, and nothing else does`() {
        assertEquals(HttpClientProvider.allowedHosts, BUILT_IN_PINS.keys)
        BUILT_IN_PINS.forEach { (host, pins) ->
            assertTrue("$host has no pins", pins.isNotEmpty())
            pins.forEach { assertTrue("$host: $it isn't a SHA-256 pin", PIN.matches(it)) }
        }
    }

    @Test
    fun `on, each host and its subdomains chain to its pins, and off, nothing is pinned`() {
        val pinner = pinnerFor(enabled = true)
        BUILT_IN_PINS.forEach { (host, pins) ->
            assertEquals(host, pins, pinsFor(pinner, host))
            assertEquals(host, pins, pinsFor(pinner, "www.$host"))
        }
        assertTrue(pinsFor(pinner, "example.com").isEmpty())
        assertSame(CertificatePinner.DEFAULT, pinnerFor(enabled = false))
    }

    @Test
    fun `the client follows the setting, and is reused while it stays the same`() {
        val pinned = HttpClientProvider.client(null)
        assertSame(pinned, HttpClientProvider.client(null))
        assertEquals(BUILT_IN_PINS.getValue(GITHUB), pinsFor(pinned.certificatePinner, GITHUB))

        CertificatePinning.update(enabled = false)
        val unpinned = HttpClientProvider.client(null)
        assertNotSame(pinned, unpinned)
        assertTrue(unpinned.certificatePinner.pins.isEmpty())
        // The same connections, threads and cache underneath.
        assertSame(pinned.connectionPool, unpinned.connectionPool)

        CertificatePinning.update(enabled = true)
        assertEquals(BUILT_IN_PINS.getValue(GITHUB), pinsFor(HttpClientProvider.client(null).certificatePinner, GITHUB))
    }

    private fun pinsFor(
        pinner: CertificatePinner,
        host: String,
    ): Set<String> = pinner.findMatchingPins(host).mapTo(HashSet()) { it.toString() }

    private companion object {
        const val GITHUB = "raw.githubusercontent.com"

        val PIN = Regex("""sha256/[A-Za-z0-9+/]{43}=""")
    }
}
