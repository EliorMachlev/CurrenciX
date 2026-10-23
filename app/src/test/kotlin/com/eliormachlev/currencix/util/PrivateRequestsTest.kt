package com.eliormachlev.currencix.util

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivateRequestsTest {
    // What reached the "network": the request after the interceptor.
    private var sent: Request? = null

    private val network =
        Interceptor { chain ->
            sent = chain.request()
            Response
                .Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("ok")
                .body("".toResponseBody())
                .build()
        }

    private val request =
        Request
            .Builder()
            .url("https://example.org/rates")
            .header("User-Agent", "okhttp/5.0.0 (Pixel 9; Android 16)")
            .header("If-None-Match", "\"tracker-1234\"")
            .header("If-Modified-Since", "Mon, 12 Oct 2026 10:00:00 GMT")
            .build()

    private fun send(enabled: Boolean) {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(PrivateRequestsInterceptor { enabled })
                .addInterceptor(network)
                .build()
        client.newCall(request).execute().close()
    }

    @Test
    fun `private requests carry the shared user agent and no cache identifiers`() {
        send(enabled = true)
        val request = checkNotNull(sent)
        assertEquals(PRIVATE_USER_AGENT, request.header("User-Agent"))
        assertNull(request.header("If-None-Match"))
        assertNull(request.header("If-Modified-Since"))
    }

    @Test
    fun `with the setting off, requests go out as they are`() {
        send(enabled = false)
        val request = checkNotNull(sent)
        assertEquals("\"tracker-1234\"", request.header("If-None-Match"))
        assertEquals("okhttp/5.0.0 (Pixel 9; Android 16)", request.header("User-Agent"))
    }
}
