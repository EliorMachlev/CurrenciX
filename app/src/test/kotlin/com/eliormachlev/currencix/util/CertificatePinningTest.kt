package com.eliormachlev.currencix.util

import com.eliormachlev.currencix.repository.decodePins
import com.eliormachlev.currencix.repository.encodePins
import okhttp3.CertificatePinner
import okhttp3.CipherSuite
import okhttp3.Handshake
import okhttp3.TlsVersion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

// Which keys each host must chain to, how Settings changes them, and how
// Re-pin reads them off a connection.
class CertificatePinningTest {
    @After
    fun backToDefaults() = CertificatePinning.update(enabled = true, userPins = emptyMap())

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
        val pinner = pinnerFor(enabled = true, userPins = emptyMap())
        BUILT_IN_PINS.forEach { (host, pins) ->
            assertEquals(host, pins, pinsFor(pinner, host))
            assertEquals(host, pins, pinsFor(pinner, "www.$host"))
        }
        assertTrue(pinsFor(pinner, "example.com").isEmpty())
        assertSame(CertificatePinner.DEFAULT, pinnerFor(enabled = false, userPins = mapOf(FRANKFURTER to setOf(ISRG_X1))))
    }

    @Test
    fun `pins taken on the phone replace that host's built-in ones only`() {
        val pinner = pinnerFor(enabled = true, userPins = mapOf(FRANKFURTER to setOf(ISRG_X1)))
        assertEquals(setOf(ISRG_X1), pinsFor(pinner, FRANKFURTER))
        assertEquals(BUILT_IN_PINS.getValue(GITHUB), pinsFor(pinner, GITHUB))
    }

    @Test
    fun `the client follows the setting, and is reused while it stays the same`() {
        val pinned = HttpClientProvider.client(null)
        assertSame(pinned, HttpClientProvider.client(null))
        assertEquals(BUILT_IN_PINS.getValue(GITHUB), pinsFor(pinned.certificatePinner, GITHUB))

        CertificatePinning.update(enabled = false, userPins = emptyMap())
        val unpinned = HttpClientProvider.client(null)
        assertNotSame(pinned, unpinned)
        assertTrue(unpinned.certificatePinner.pins.isEmpty())
        // The same connections, threads and cache underneath.
        assertSame(pinned.connectionPool, unpinned.connectionPool)
        assertTrue(
            HttpClientProvider
                .unpinnedClient()
                .certificatePinner.pins
                .isEmpty(),
        )
    }

    @Test
    fun `re-pin keeps every authority of the verified chain but the server's own certificate`() {
        val (server, intermediate, root) = listOf("gts-root-r4", "isrg-root-x2", "isrg-root-x1").map(::certificate)
        val handshake =
            Handshake.get(
                TlsVersion.TLS_1_3,
                CipherSuite.TLS_AES_128_GCM_SHA256,
                listOf(server, intermediate, root),
                emptyList(),
            )
        assertEquals(setOf(ISRG_X2, ISRG_X1), handshake.authorityPins())
    }

    @Test
    fun `pins taken on the phone survive their storage, and broken entries are skipped`() {
        val pins = mapOf(FRANKFURTER to setOf(ISRG_X1, ISRG_X2), GITHUB to setOf(ISRG_X1))
        assertEquals(pins, decodePins(encodePins(pins)))
        assertEquals(
            mapOf(GITHUB to setOf(ISRG_X1)),
            decodePins(setOf("$GITHUB $ISRG_X1", "$FRANKFURTER sha1/abc", " $ISRG_X2", "$GITHUB", "$GITHUB sha256/")),
        )
    }

    private fun pinsFor(
        pinner: CertificatePinner,
        host: String,
    ): Set<String> = pinner.findMatchingPins(host).mapTo(HashSet()) { it.toString() }

    private fun certificate(name: String): X509Certificate =
        checkNotNull(javaClass.getResourceAsStream("/certificates/$name.pem")).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }

    private companion object {
        const val FRANKFURTER = "api.frankfurter.dev"
        const val GITHUB = "raw.githubusercontent.com"

        // The public keys of two Let's Encrypt roots, as every pinning guide lists them.
        const val ISRG_X1 = "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="
        const val ISRG_X2 = "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI="

        val PIN = Regex("""sha256/[A-Za-z0-9+/]{43}=""")
    }
}
