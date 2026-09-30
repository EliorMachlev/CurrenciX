package com.eliormachlev.currencix.util

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Rewrites `Cache-Control` on responses from a small, hand-picked allowlist of
 * exchange-rate providers whose upstreams either omit the header entirely or
 * emit `no-cache` even though the underlying data changes only on a known,
 * slow cadence (business-day / hourly / monthly — see
 * `docs/markDown/api-providers.md`).
 *
 * ### Why an allowlist rather than a blanket rewrite
 * Stamping `max-age` onto arbitrary hosts would poison legitimately dynamic
 * responses (auth, Chucker, future providers we haven't audited). The
 * allowlist means we only shorten the round-trip for hosts whose caching
 * semantics we've verified — safety over flexibility.
 *
 * ### TTL policy
 * The stamped `max-age` is deliberately *shorter* than the upstream update
 * cadence — a daily provider gets an hour, a monthly provider gets six hours.
 * That way a stale cache never outlives a working day even if the app is
 * offline when the upstream publishes its next value.
 *
 * ### Interaction with the shared OkHttp cache
 * OkHttp consults the response's `Cache-Control` when deciding whether the
 * response is storable. Because this interceptor runs as a *network*
 * interceptor (added to `networkInterceptors`), the rewrite happens before
 * the cache writer sees the response — so the stamped header controls
 * subsequent hits.
 */
internal class ProviderCacheRewriteInterceptor(
    private val hostAllowlist: Map<String, Long>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val host = chain.request().url.host
        val ttlSeconds = hostAllowlist[host] ?: return response
        return response
            .newBuilder()
            .removeHeader("Pragma")
            .header("Cache-Control", "public, max-age=$ttlSeconds")
            .build()
    }
}

/**
 * Per-host TTL for the rewrite interceptor. Keys are `okhttp3.HttpUrl.host`
 * values (no scheme, no port). TTLs derive from the upstream update cadence
 * documented in `docs/markDown/api-providers.md`:
 *
 *  - Business-day publishers → 1 h (`3600`): safely inside a working day so
 *    the next publish will be picked up within an hour of app foregrounding.
 *  - Monthly publisher (InforEuro) → 6 h (`21600`): the underlying rate table
 *    only changes on the first of the month, so a leisurely TTL still leaves
 *    plenty of headroom.
 *
 * Cooperative hosts (Frankfurter, OpenExchangerates) are *not* in this map —
 * their own `Cache-Control` headers are honoured verbatim.
 */
internal val PROVIDER_CACHE_TTL_SECONDS: Map<String, Long> =
    mapOf(
        // Bank of Canada Valet — daily FX_RATES_DAILY_CURRENT.
        "www.bankofcanada.ca" to TimeUnit.HOURS.toSeconds(1),
        // Norges Bank SDMX — daily business-day rates.
        "data.norges-bank.no" to TimeUnit.HOURS.toSeconds(1),
        // Bank Rossii XML feeds — daily business-day rates.
        "www.cbr.ru" to TimeUnit.HOURS.toSeconds(1),
        // Bank of Israel PublicApi — daily business-day rates.
        "boi.org.il" to TimeUnit.HOURS.toSeconds(1),
        // Bank of Israel SDMX — daily business-day rates (separate host).
        "edge.boi.gov.il" to TimeUnit.HOURS.toSeconds(1),
        // EU Commission InforEuro — monthly accounting rates.
        "ec.europa.eu" to TimeUnit.HOURS.toSeconds(6),
    )
