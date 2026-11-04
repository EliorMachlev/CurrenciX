package com.eliormachlev.currencix.util

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Retrofit

/**
 * Builds a [Retrofit] instance layered on top of the shared [okhttp3.OkHttpClient]
 * from [HttpClientProvider] — so the on-disk cache and the per-provider
 * `Cache-Control` rewrite interceptor (see [ProviderCacheRewriteInterceptor])
 * apply uniformly to Retrofit-backed and raw-OkHttp providers alike.
 *
 * A fresh [Retrofit] is cheap to build (no I/O, no reflection scan until an
 * interface is created), so per-provider builders don't need to be cached;
 * the `OkHttpClient` it closes over is the expensive singleton, and it's
 * already hoisted.
 *
 * No converter: every interface returns the raw body, which the provider's
 * own adapter reads as a JSON tree (see `fetchRetrofit`).
 */
object RetrofitProvider {
    /**
     * @param context passed straight through to [HttpClientProvider.client].
     *   Non-null in production callers (Application context); tests may pass
     *   null to get a cache-less client with the same timeouts / logging.
     * @param baseUrl the provider's base URL. Retrofit requires this to end in
     *   `/`; the helper appends one if the caller forgot.
     */
    fun retrofit(
        context: Context?,
        baseUrl: String,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .callFactory(RevalidatingCallFactory(HttpClientProvider.client(context)))
            .build()
}

/**
 * Runs a Retrofit suspend call and returns [Result], preserving the same
 * error surface as [HttpClientProvider.fetch]:
 *
 *  - Non-2xx responses turn into [ApiHttpError] (Retrofit surfaces them as
 *    [HttpException] — translated here so the repository's error handler,
 *    which pattern-matches on [ApiHttpError], keeps working unchanged).
 *  - Network failures propagate as their original exception type
 *    (`SocketTimeoutException`, `IOException`, …).
 *  - Decoding failures propagate as whatever the provider's adapter or the
 *    JSON parser throws.
 *
 * Runs on [dispatcher] (the IO one) so response body decoding doesn't touch
 * the main thread even though Retrofit's own dispatcher already backgrounds
 * the HTTP call itself.
 */
suspend fun <T> retrofitCall(
    dispatcher: CoroutineDispatcher = AppDispatchers.production.io,
    block: suspend () -> T,
): Result<T> =
    withContext(dispatcher) {
        runCatching {
            try {
                block()
            } catch (e: HttpException) {
                // Translate Retrofit's HttpException to the repo-wide
                // ApiHttpError so ExchangeRatesRepository's error handler
                // can pattern-match by type instead of exception message.
                // Keep the original as `cause` so nothing is swallowed.
                throw ApiHttpError(e.code(), e.message()).apply { initCause(e) }
            }
        }
    }
