package com.eliormachlev.currencix.model.provider

import com.eliormachlev.currencix.util.HttpClientProvider
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.rules.ExternalResource
import java.lang.reflect.Modifier

private const val HTTP_OK = 200

/**
 * Runs the real provider code with no network: every request is recorded in
 * [requests] and answered with [status] and [body]. Providers reach HTTP
 * through [HttpClientProvider]'s context-less client, which this swaps for
 * the duration of a test.
 */
class StubHttpRule : ExternalResource() {
    val requests = mutableListOf<HttpUrl>()
    var status = HTTP_OK

    // A JSON `null` by default: "no data" to the adapters that accept a
    // non-object payload.
    var body = "null"

    /** Bodies for particular requests, by the end of their URL path; anything else gets [body]. */
    val bodyByPathEnd = mutableMapOf<String, String>()

    private fun bodyFor(url: HttpUrl): String = bodyByPathEnd.entries.firstOrNull { url.encodedPath.endsWith(it.key) }?.value ?: body

    private val recordingClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                requests += chain.request().url
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("stub")
                    .body(bodyFor(chain.request().url).toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()

    // HttpClientProvider has no injection seam, and adding one to production
    // code just for tests isn't worth it — set its context-less client slot
    // directly. Fails loudly if the field is renamed.
    private val uncachedClientField =
        HttpClientProvider::class.java.getDeclaredField("uncachedInstance").apply { isAccessible = true }
    private val fieldOwner: Any? =
        if (Modifier.isStatic(uncachedClientField.modifiers)) null else HttpClientProvider

    override fun before() = uncachedClientField.set(fieldOwner, recordingClient)

    override fun after() = uncachedClientField.set(fieldOwner, null)
}
