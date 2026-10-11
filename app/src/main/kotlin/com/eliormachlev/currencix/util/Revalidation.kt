package com.eliormachlev.currencix.util

import kotlinx.coroutines.asContextElement
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

// Set while a fetch the user asked for is running, on whichever thread its
// coroutine is on at the moment (see revalidating).
private val revalidatingFlag = ThreadLocal<Boolean>()

// "Check with the server": a cached answer is never fresh enough to skip it.
private val CHECK_WITH_SERVER = CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build()

/**
 * Coroutine context for a fetch the user asked for (pull to refresh, the
 * scheduled refresh): requests made under it ask the server whether the
 * cached answer still holds (OkHttp sends its ETag or date as
 * `If-None-Match` / `If-Modified-Since`), so an unchanged answer comes back
 * as a few bytes instead of in full, and a changed one isn't missed for the
 * cache's sake. Without it, a request the cache can answer never leaves the
 * phone.
 */
internal fun revalidating(): CoroutineContext = revalidatingFlag.asContextElement(true)

/** [this] request, made to check with the server first when the fetch runs under [revalidating]. */
internal fun Request.checkedWithServerIfAsked(): Request =
    if (revalidatingFlag.get() == true) newBuilder().cacheControl(CHECK_WITH_SERVER).build() else this

/** [client]'s calls, each request passed through [checkedWithServerIfAsked] (for Retrofit). */
internal class RevalidatingCallFactory(
    private val client: OkHttpClient,
) : Call.Factory {
    override fun newCall(request: Request): Call = client.newCall(request.checkedWithServerIfAsked())
}
