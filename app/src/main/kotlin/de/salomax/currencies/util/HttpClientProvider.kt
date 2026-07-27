package de.salomax.currencies.util

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

private const val CACHE_DIR = "http-cache"
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
// usable Cache-Control headers.
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
        }
        return builder.build()
    }
}
