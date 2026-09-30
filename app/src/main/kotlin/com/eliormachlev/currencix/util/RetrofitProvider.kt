package com.eliormachlev.currencix.util

import android.content.Context
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Builds a [Retrofit] instance layered on top of the shared [okhttp3.OkHttpClient]
 * from [HttpClientProvider] — so the on-disk cache and the per-provider
 * `Cache-Control` rewrite interceptor (see [ProviderCacheRewriteInterceptor])
 * apply uniformly to Retrofit-backed and raw-OkHttp providers alike.
 *
 * A fresh [Retrofit] is cheap to build (no I/O, no reflection scan until an
 * interface is created), so per-provider builders don't need to be cached; the
 * `OkHttpClient` and `Moshi` instances they close over are the expensive
 * singletons and both are already hoisted.
 */
object RetrofitProvider {
    /**
     * @param context passed straight through to [HttpClientProvider.client].
     *   Non-null in production callers (Application context); tests may pass
     *   null to get a cache-less client with the same timeouts / logging.
     * @param baseUrl the provider's base URL. Retrofit requires this to end in
     *   `/`; the helper appends one if the caller forgot.
     * @param moshi the preconfigured [Moshi] instance — providers add their
     *   per-request custom adapters via [com.eliormachlev.currencix.model.provider.moshi].
     */
    fun retrofit(
        context: Context?,
        baseUrl: String,
        moshi: Moshi,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(HttpClientProvider.client(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
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
 *  - Deserialization failures propagate as whatever the converter throws.
 *
 * Runs on [Dispatchers.IO] so response body decoding doesn't touch the main
 * thread even though Retrofit's own dispatcher already backgrounds the HTTP
 * call itself.
 */
suspend fun <T> retrofitCall(block: suspend () -> T): Result<T> =
    withContext(Dispatchers.IO) {
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
