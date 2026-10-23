package com.eliormachlev.currencix.util

import okhttp3.Interceptor
import okhttp3.Response

// What every install says it is when requests are private: no app version,
// no OkHttp version, nothing about the phone.
internal const val PRIVATE_USER_AGENT = "CurrenciX"

// A server can set an ETag or Last-Modified that is unique to one phone and
// read it back when the cache revalidates: a cookie in all but name. Private
// requests never send them, at the cost of a full answer instead of a 304.
private val CACHE_IDENTIFIERS = listOf("If-None-Match", "If-Modified-Since")

/**
 * Settings → Security & privacy → Private requests, as the HTTP client
 * reads it (kept in step with PrivacySettings.privateRequests by
 * CurrenciesApplication).
 */
object PrivateRequests {
    @Volatile
    var enabled: Boolean = false
}

/**
 * With [enabled], every request leaves the phone the same as everyone
 * else's: the shared [PRIVATE_USER_AGENT] and no cache identifiers. A
 * network interceptor, so it sees what OkHttp's cache and bridge added.
 */
internal class PrivateRequestsInterceptor(
    private val enabled: () -> Boolean = { PrivateRequests.enabled },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enabled()) return chain.proceed(chain.request())
        val request =
            chain
                .request()
                .newBuilder()
                .header("User-Agent", PRIVATE_USER_AGENT)
                .apply { CACHE_IDENTIFIERS.forEach(::removeHeader) }
                .build()
        return chain.proceed(request)
    }
}
