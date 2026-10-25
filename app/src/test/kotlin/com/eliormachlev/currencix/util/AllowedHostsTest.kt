package com.eliormachlev.currencix.util

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.ocr.TessLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowedHostsTest {
    @Test
    fun `every provider's hosts are allowed, its other feeds included`() {
        assertEquals(
            setOf(
                "api.frankfurter.dev",
                "openexchangerates.org",
                "allratestoday.com",
                "ec.europa.eu",
                "www.bankofcanada.ca",
                "data.norges-bank.no",
                "www.cbr.ru",
                "boi.org.il",
                "edge.boi.gov.il",
            ),
            ApiProvider.allHosts,
        )
    }

    @Test
    fun `a host or a subdomain of one passes, anything else doesn't`() {
        val allowed = ApiProvider.allHosts + TessLanguage.DOWNLOAD_HOST
        assertTrue(isAllowedHost("boi.org.il", allowed))
        assertTrue(isAllowedHost("www.boi.org.il", allowed))
        assertTrue(isAllowedHost("RAW.githubusercontent.com", allowed))
        assertFalse(isAllowedHost("example.com", allowed))
        // A look-alike that only ends in an allowed name's letters.
        assertFalse(isAllowedHost("evilboi.org.il", allowed))
        assertFalse(isAllowedHost("boi.org.il.example.com", allowed))
        assertFalse(isAllowedHost("githubusercontent.com", allowed))
    }
}
