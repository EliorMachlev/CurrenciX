package com.eliormachlev.currencix.util

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RevalidationTest {
    private val request = Request.Builder().url("https://example.org/rates").build()

    @Test
    fun `an ordinary fetch lets the cache answer`() {
        assertNull(request.checkedWithServerIfAsked().header("Cache-Control"))
    }

    @Test
    fun `a fetch the user asked for checks with the server, on whatever thread it runs`() =
        runBlocking {
            val header =
                withContext(
                    revalidating(),
                ) { withContext(AppDispatchers.production.io) { request.checkedWithServerIfAsked().header("Cache-Control") } }
            assertEquals("max-age=0", header)
            // And only inside it.
            assertNull(request.checkedWithServerIfAsked().header("Cache-Control"))
        }
}
