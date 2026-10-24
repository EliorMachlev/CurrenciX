package com.eliormachlev.currencix.util

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Refuses any request to a host outside [allowed] (or a subdomain of one):
 * the app talks to its rate providers and the OCR language host, nothing
 * else. A network interceptor, so it sees every hop of a redirect too, and a
 * bug or a redirect can't send a request, or a key, anywhere else.
 */
internal class AllowedHostsInterceptor(
    private val allowed: () -> Set<String>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (!isAllowedHost(host, allowed())) throw IOException("$host isn't a host this app talks to")
        return chain.proceed(chain.request())
    }
}

/** Whether [host] is one of [allowed] or a subdomain of one ("www.boi.org.il" under "boi.org.il"). */
internal fun isAllowedHost(
    host: String,
    allowed: Set<String>,
): Boolean = allowed.any { host.equals(it, ignoreCase = true) || host.endsWith(".$it", ignoreCase = true) }
