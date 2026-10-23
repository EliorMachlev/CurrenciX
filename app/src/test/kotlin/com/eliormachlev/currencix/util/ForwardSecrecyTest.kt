package com.eliormachlev.currencix.util

import okhttp3.TlsVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Every connection the app makes keeps forward secrecy: TLS 1.3, or TLS 1.2
// with an ephemeral (ECDHE) key exchange. Nothing falls back to RSA key
// exchange, 3DES or cleartext, as OkHttp's default would.
class ForwardSecrecyTest {
    private val client = HttpClientProvider.client(null)

    @Test
    fun `the client uses only the forward-secret spec`() = assertEquals(listOf(FORWARD_SECRET_TLS), client.connectionSpecs)

    @Test
    fun `only TLS 1_3 and 1_2`() = assertEquals(listOf(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2), FORWARD_SECRET_TLS.tlsVersions)

    @Test
    fun `every TLS 1_2 suite is ECDHE with an AEAD cipher`() {
        val suites = FORWARD_SECRET_TLS.cipherSuites.orEmpty().map { it.javaName }
        assertTrue(suites.isNotEmpty())
        suites.filterNot { it in TLS_1_3_SUITES }.forEach { suite ->
            assertTrue(suite, suite.startsWith("TLS_ECDHE_") && (suite.contains("_GCM_") || suite.contains("CHACHA20_POLY1305")))
        }
    }

    private companion object {
        val TLS_1_3_SUITES = setOf("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256")
    }
}
