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
private const val READ_TIMEOUT_SECONDS = 15L

// Shared OkHttp client with an on-disk response cache and Timber-bridged
// wire logging. Rate providers still call Fuel today; the cache warms up
// only if the provider's response carries Cache-Control headers or is
// wrapped by a forthcoming Retrofit-based service that sets its own policy.
object HttpClientProvider {
    @Volatile
    private var instance: OkHttpClient? = null

    fun client(context: Context): OkHttpClient =
        instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

    private fun build(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val loggingInterceptor =
            HttpLoggingInterceptor { line ->
                Timber.tag("HTTP").d(line)
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        return OkHttpClient
            .Builder()
            .cache(Cache(cacheDir, CACHE_SIZE_BYTES))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
