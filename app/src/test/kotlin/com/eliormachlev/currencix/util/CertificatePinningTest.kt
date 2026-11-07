package com.eliormachlev.currencix.util

import com.eliormachlev.currencix.architecture.repositoryRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import java.security.cert.CertificateException
import java.time.LocalDate
import javax.net.ssl.SSLHandshakeException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

// Which keys each host must chain to (res/xml/network_security_config.xml),
// and how a refused pin is told apart from other failures.
class CertificatePinningTest {
    private val configs: List<Element> by lazy {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature(DISALLOW_DOCTYPE, true)
            }
        val document = factory.newDocumentBuilder().parse(File(repositoryRoot(), CONFIG))
        document.getElementsByTagName("domain-config").elements()
    }

    @Test
    fun `every host the app may reach has pins, its subdomains too, and nothing else does`() {
        val hosts =
            configs.map { config ->
                val domain = config.getElementsByTagName("domain").elements().single()
                assertEquals(domain.textContent, "true", domain.getAttribute("includeSubdomains"))
                val pins = config.getElementsByTagName("pin").elements()
                assertTrue("${domain.textContent} has no pins", pins.isNotEmpty())
                pins.forEach { pin ->
                    assertEquals("SHA-256", pin.getAttribute("digest"))
                    assertTrue("${domain.textContent}: ${pin.textContent} isn't a SHA-256 pin", PIN.matches(pin.textContent))
                }
                domain.textContent
            }
        assertEquals(HttpClientProvider.allowedHosts, hosts.toSet())
        assertEquals("a host listed twice", hosts.size, hosts.toSet().size)
    }

    @Test
    fun `every pin set expires on the same written date`() {
        val expirations = configs.flatMap { it.getElementsByTagName("pin-set").elements() }.map { it.getAttribute("expiration") }
        assertEquals(configs.size, expirations.size)
        assertEquals(1, expirations.toSet().size)
        LocalDate.parse(expirations.first())
    }

    @Test
    fun `a refused pin is told apart from other certificate failures`() {
        assertTrue(SSLHandshakeException("handshake failed").apply { initCause(CertificateException(PIN_FAILURE)) }.isPinFailure())
        assertFalse(SSLHandshakeException("handshake failed").apply { initCause(CertificateException("expired")) }.isPinFailure())
        assertFalse(SSLHandshakeException(PIN_FAILURE).isPinFailure())
    }

    private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

    private companion object {
        const val CONFIG = "app/src/main/res/xml/network_security_config.xml"
        const val DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
        const val PIN_FAILURE = "Pin verification failed"

        // The base64 SHA-256 of a SubjectPublicKeyInfo.
        val PIN = Regex("""[A-Za-z0-9+/]{43}=""")
    }
}
