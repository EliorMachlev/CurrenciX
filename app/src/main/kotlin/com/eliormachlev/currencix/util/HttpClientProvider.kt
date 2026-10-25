package com.eliormachlev.currencix.util

import android.content.Context
import android.os.Build
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.ocr.TessLanguage
import okhttp3.Cache
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import okhttp3.android.AndroidDns
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

// TLS with forward secrecy only: TLS 1.3 (whose key exchange is always
// ephemeral), and TLS 1.2 only with ECDHE and an AEAD cipher. OkHttp's
// default (MODERN_TLS) still accepts RSA key exchange and 3DES as a fallback,
// under which a provider's private key, if it ever leaked, would decrypt
// recorded traffic. Every provider offers TLS 1.3 or ECDHE, and minSdk 33
// supports all of these. No cleartext either, as network_security_config.xml.
internal val FORWARD_SECRET_TLS: ConnectionSpec =
    ConnectionSpec
        .Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
        .cipherSuites(
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
        ).build()

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

    @Volatile
    private var downloadInstance: OkHttpClient? = null

    /**
     * Returns the shared client. When [context] is non-null the returned
     * client has an on-disk response cache under `cacheDir/http-cache`;
     * when [context] is null (unit tests, background workers without a
     * Context) the cache-less base client is returned so the cache
     * directory doesn't have to be faked. Both are one client underneath
     * ([OkHttpClient.newBuilder]): the same connections, threads and timeouts.
     */
    fun client(context: Context?): OkHttpClient =
        if (context != null) {
            cachedInstance ?: synchronized(this) {
                cachedInstance ?: withCache(base(), context).also { cachedInstance = it }
            }
        } else {
            base()
        }

    /**
     * For a large one-off download (an OCR language file, a few megabytes):
     * the shared client without its call time limit or its cache.
     */
    fun downloadClient(context: Context): OkHttpClient =
        downloadInstance ?: synchronized(this) {
            downloadInstance ?: client(context)
                .newBuilder()
                .cache(null)
                .callTimeout(0, TimeUnit.SECONDS)
                .build()
                .also { downloadInstance = it }
        }

    private fun base(): OkHttpClient =
        uncachedInstance ?: synchronized(this) {
            uncachedInstance ?: build().also { uncachedInstance = it }
        }

    private fun build(): OkHttpClient {
        val loggingInterceptor =
            HttpLoggingInterceptor { line ->
                Timber.tag("HTTP").d(line)
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        return OkHttpClient
            .Builder()
            .addInterceptor(loggingInterceptor)
            // Network-layer rewrite runs before OkHttp's cache writer sees
            // the response, so the stamped Cache-Control controls what the
            // cache stores for the non-cooperative provider hosts.
            // Only the rate providers' hosts and the OCR language host, at every redirect.
            .addNetworkInterceptor(AllowedHostsInterceptor { ApiProvider.allHosts + TessLanguage.DOWNLOAD_HOST })
            .addNetworkInterceptor(ProviderCacheRewriteInterceptor(PROVIDER_CACHE_TTL_SECONDS))
            // Innermost, so the request it shapes is exactly what leaves the phone.
            .addNetworkInterceptor(PrivateRequestsInterceptor())
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Belt-and-braces: cap total call time as well, so an HTTP/2
            // stream that stalls between frames can't outlive the per-frame
            // read timeout indefinitely.
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionSpecs(listOf(FORWARD_SECRET_TLS))
            .apply { encryptedClientHelloDns()?.let(::dns) }
            .build()
    }

    // On Android 17 and later, resolve through the platform's DnsResolver
    // (OkHttp's AndroidDns), which also fetches each host's DNS HTTPS record
    // with its Encrypted Client Hello keys: the TLS handshake then hides which
    // provider the app is talking to from anyone watching the network, where
    // the host offers ECH (others connect as before). Earlier versions can't
    // do ECH, so they keep the system resolver they always used.
    private fun encryptedClientHelloDns(): Dns? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) AndroidDns() else null

    // The base client plus the on-disk cache and, in debug builds, the
    // in-app HTTP inspector (Chucker; the build type decides, see
    // httpInspector). Added last so its capture sees the fully-decorated
    // request.
    private fun withCache(
        base: OkHttpClient,
        context: Context,
    ): OkHttpClient {
        val cacheDir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val builder = base.newBuilder().cache(Cache(cacheDir, CACHE_SIZE_BYTES))
        httpInspector?.let { inspector -> builder.addInterceptor(inspector(context)) }
        return builder.build()
    }
}
