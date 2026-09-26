package com.eliormachlev.currencix.util

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

private const val CACHE_DIR = "http-cache"

// 5 MiB is a generous ceiling for our workload: each provider's live-rates
// payload is single-digit KB (JSON) to ~40 KB (XML with metadata), and even
// the timeline endpoints top out around a few hundred KB. Five megabytes
// comfortably holds hundreds of distinct responses without eating meaningfully
// into the app's cache-quota budget on low-storage devices.
private const val CACHE_SIZE_BYTES = 5L * 1024L * 1024L

private const val CONNECT_TIMEOUT_SECONDS = 15L

// Read timeout is deliberately generous: some upstream providers (Cloudflare-
// fronted Frankfurter, InforEuro's Europa.eu host) take several seconds to
// respond during cold-start / warmup, and CI runners have flakier network
// baselines than dev machines. 30 s comfortably absorbs that without letting
// a truly dead endpoint hang the UI thread for too long.
private const val READ_TIMEOUT_SECONDS = 30L
private const val CALL_TIMEOUT_SECONDS = 45L

// Shared OkHttp client with an on-disk response cache and Timber-bridged
// wire logging. Every rate provider funnels through this client via
// HttpFetch.kt; the cache warms up whenever the upstream response carries
// usable Cache-Control headers. Providers whose upstreams do not send
// usable headers get their responses stamped with a per-host TTL by
// [ProviderCacheRewriteInterceptor] — see api-providers.md for the matrix.
object HttpClientProvider {
    @Volatile
    private var cachedInstance: OkHttpClient? = null

    @Volatile
    private var uncachedInstance: OkHttpClient? = null

    /**
     * Returns the shared client. When [context] is non-null the returned
     * client has an on-disk response cache under `cacheDir/http-cache`;
     * when [context] is null (unit tests, background workers without a
     * Context) a cache-less client with the same timeouts / logging is
     * returned so the cache directory doesn't have to be faked.
     */
    fun client(context: Context?): OkHttpClient =
        if (context != null) {
            cachedInstance ?: synchronized(this) {
                cachedInstance ?: build(context).also { cachedInstance = it }
            }
        } else {
            uncachedInstance ?: synchronized(this) {
                uncachedInstance ?: build(null).also { uncachedInstance = it }
            }
        }

    private fun build(context: Context?): OkHttpClient {
        val loggingInterceptor =
            HttpLoggingInterceptor { line ->
                Timber.tag("HTTP").d(line)
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        val builder =
            OkHttpClient
                .Builder()
                .addInterceptor(loggingInterceptor)
                // Network-layer rewrite runs before OkHttp's cache writer sees
                // the response, so the stamped Cache-Control controls what the
                // cache stores for the non-cooperative provider hosts.
                .addNetworkInterceptor(ProviderCacheRewriteInterceptor(PROVIDER_CACHE_TTL_SECONDS))
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // Belt-and-braces: cap total call time as well, so an HTTP/2
                // stream that stalls between frames can't outlive the per-frame
                // read timeout indefinitely.
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
        if (context != null) {
            val cacheDir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
            builder.cache(Cache(cacheDir, CACHE_SIZE_BYTES))
            // Chucker in-app HTTP inspector — a real interceptor in debug
            // builds, a pass-through no-op in release. Only wired when we
            // have a Context (background workers / unit tests hit the null
            // path and don't need the inspector). Added last so its capture
            // sees the fully-decorated request.
            builder.addInterceptor(ChuckerInterceptorProvider.create(context))
        }
        return builder.build()
    }
}
