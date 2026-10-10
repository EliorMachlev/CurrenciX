package com.eliormachlev.currencix.util

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
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
 * Parsing runs on [dispatcher] (the IO one) alongside the request — most
 * parsers here pull XML off a stream, which is I/O-bound anyway.
 */
suspend fun <T> HttpClientProvider.fetch(
    context: Context?,
    url: String,
    dispatcher: CoroutineDispatcher = AppDispatchers.production.io,
    parse: (ResponseBody) -> T,
): Result<T> =
    withContext(dispatcher) {
        runCatching {
            val request = Request.Builder().url(url).build()
            val response = client(context).newCall(request).await()
            response.use {
                if (!it.isSuccessful) throw ApiHttpError(it.code)
                parse(it.body)
            }
        }
    }
