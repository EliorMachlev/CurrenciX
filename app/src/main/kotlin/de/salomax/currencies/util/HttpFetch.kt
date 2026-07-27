package de.salomax.currencies.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thrown when a request completed but returned a non-2xx status. Distinct
 * class so the repository's error handler can format an "HTTP nnn" message
 * without inspecting message strings. Network-layer failures (timeouts,
 * DNS, socket resets) propagate as their original exception types so the
 * same handler can pattern-match on them (SocketTimeoutException etc.).
 */
class ApiHttpError(
    val statusCode: Int,
    message: String? = null,
) : IOException(message ?: "HTTP $statusCode")

/**
 * Suspend-friendly wrapper around [Call.enqueue] that supports cancellation.
 * OkHttp 4.x doesn't ship a coroutine adapter — this is the standard bridge.
 */
private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    cont.resume(response)
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    cont.resumeWithException(e)
                }
            },
        )
    }

/**
 * Execute [url] on the shared OkHttp client and hand the response body to
 * [parse]. Wraps the whole thing in [kotlin.Result] so callers can chain
 * `.map { … }` on the outcome.
 *
 *  - Non-2xx responses turn into [ApiHttpError]
 *  - Network failures propagate as their original exception type
 *  - Deserialization failures propagate as whatever [parse] throws
 *
 * Parsing runs on [Dispatchers.IO] alongside the request — most parsers here
 * pull XML off a stream, which is I/O-bound anyway.
 */
suspend fun <T> HttpClientProvider.fetch(
    context: Context?,
    url: String,
    parse: (ResponseBody) -> T,
): Result<T> =
    withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            val response = client(context).newCall(request).await()
            response.use {
                if (!it.isSuccessful) throw ApiHttpError(it.code)
                val body = it.body ?: throw IOException("Empty response body")
                parse(body)
            }
        }
    }
