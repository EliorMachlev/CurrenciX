package com.eliormachlev.currencix.repository.cache

import android.content.Context
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.repository.ExchangeRatesService
import java.io.File
import java.util.concurrent.TimeUnit

// Sub-directory under `context.cacheDir` that owns every RateCache-managed
// file. Segregated from OkHttp's `http-cache` so a `cacheDir.deleteRecursively`
// invoked from a debug menu (or a future "clear cache" preference) still
// leaves the other tier untouched.
private const val RATE_CACHE_DIR = "rate-cache"
private const val RATES_SUBDIR = "rates"
private const val TIMELINE_SUBDIR = "timeline"

// Per-provider TTL defaults, chosen so the in-house cache never serves data
// staler than the OkHttp rewrite interceptor would (see
// `ProviderCacheRewriteInterceptor.PROVIDER_CACHE_TTL_SECONDS`). Callers can
// still override per-key via `RateCache`'s `ttlFor` constructor parameter.
private val PROVIDER_TTL_MS: Map<ApiProvider, Long> =
    mapOf(
        // Hourly upstream; keep the app tier well below to avoid serving
        // last-hour data when the user explicitly refreshes.
        ApiProvider.FRANKFURTER_APP to TimeUnit.MINUTES.toMillis(15),
        ApiProvider.OPEN_EXCHANGERATES to TimeUnit.MINUTES.toMillis(15),
        // Business-day publishers — 30 minutes covers the "user opens app
        // twice in a lunch break" case without pinging idle upstreams.
        ApiProvider.BANK_OF_CANADA to TimeUnit.MINUTES.toMillis(30),
        ApiProvider.NORGES_BANK to TimeUnit.MINUTES.toMillis(30),
        ApiProvider.BANK_ROSSII to TimeUnit.MINUTES.toMillis(30),
        ApiProvider.BANK_OF_ISRAEL to TimeUnit.MINUTES.toMillis(30),
        // Monthly publisher — leisurely TTL since rates only change on the
        // first of the month.
        ApiProvider.INFOR_EURO to TimeUnit.HOURS.toMillis(2),
    )

private fun ttlFor(provider: ApiProvider): Long = PROVIDER_TTL_MS[provider] ?: RateCache.DEFAULT_TTL_MS

/**
 * Builders for the two [RateCache] shapes used by [com.eliormachlev.currencix
 * .repository.ExchangeRatesRepository]. Kept in one file so the wiring is
 * discoverable in a single glance: SoT construction, Fetcher lambdas, and
 * TTL policy all sit next to each other rather than scattered across the
 * package.
 *
 * Both caches sit *on top* of the shared OkHttp Cache (5 MiB, per-provider
 * `Cache-Control` rewrite interceptor). Layered caches are intentional —
 * OkHttp caches transport bytes, RateCache caches parsed domain objects.
 * Every hit that skips the parser is a hit that skips a moshi/XML pipeline.
 */
object RateCacheFactory {
    /**
     * @param secretsSupplier resolved per fetch so provider secrets edited in
     *   Settings apply immediately without cache reconstruction. Called on the
     *   coroutine's dispatcher (IO) — safe to hit DataStore snapshot reads.
     */
    fun buildRatesCache(
        context: Context,
        secretsSupplier: () -> ApiSecrets = { ApiSecrets.EMPTY },
    ): RateCache<RateCacheKey.RatesLatest, ExchangeRates> {
        val rootDir = File(context.cacheDir, "$RATE_CACHE_DIR/$RATES_SUBDIR")
        return RateCache(
            sourceOfTruth = DiskJsonStore(rootDir, ExchangeRatesConverter),
            fetcher =
                Fetcher { key ->
                    ExchangeRatesService.getRates(
                        apiProvider = ApiProvider.fromId(key.providerId),
                        date = key.date,
                        context = context,
                        secrets = secretsSupplier(),
                    )
                },
            ttlFor = { key -> ttlFor(ApiProvider.fromId(key.providerId)) },
        )
    }

    fun buildTimelineCache(context: Context): RateCache<RateCacheKey.TimelineRange, Timeline> {
        val rootDir = File(context.cacheDir, "$RATE_CACHE_DIR/$TIMELINE_SUBDIR")
        // Timeline persistence is per-pair; the converter needs the symbol
        // currency so the on-disk map keys reconstruct with the right Rate
        // shape. We resolve it lazily per read via the key.
        return RateCache(
            sourceOfTruth = TimelineDiskStore(rootDir),
            fetcher =
                Fetcher { key ->
                    ExchangeRatesService.getTimeline(
                        apiProvider = ApiProvider.fromId(key.providerId),
                        base = requireCurrency(key.baseIso),
                        symbol = requireCurrency(key.symbolIso),
                        startDate = key.startDate,
                        endDate = key.endDate,
                        context = context,
                    )
                },
            ttlFor = { key -> ttlFor(ApiProvider.fromId(key.providerId)) },
        )
    }

    private fun requireCurrency(iso: String): Currency =
        Currency.fromString(iso)
            ?: error("Unknown currency ISO code in cache key: $iso")
}

/**
 * Wraps [DiskJsonStore] with per-key [TimelineConverter] resolution. The
 * symbol currency is embedded in [RateCacheKey.TimelineRange] rather than
 * on the [Timeline] payload, so decoding needs the key context — hence the
 * indirection instead of a plain `DiskJsonStore<Timeline>`.
 */
private class TimelineDiskStore(
    private val rootDir: File,
) : SourceOfTruth<Timeline> {
    override suspend fun read(key: RateCacheKey): CachedEntry<Timeline>? = storeFor(key).read(key)

    override suspend fun write(
        key: RateCacheKey,
        value: Timeline,
    ) {
        storeFor(key).write(key, value)
    }

    override suspend fun clear(key: RateCacheKey) {
        storeFor(key).clear(key)
    }

    override suspend fun clearAll() {
        // clearAll doesn't need a specific converter — any Converter suffices
        // because DiskJsonStore.clearAll just wipes the directory.
        DiskJsonStore(rootDir, TimelineConverter(Currency.EUR)).clearAll()
    }

    private fun storeFor(key: RateCacheKey): DiskJsonStore<Timeline> {
        val symbolIso =
            (key as? RateCacheKey.TimelineRange)?.symbolIso
                ?: error("TimelineDiskStore only accepts RateCacheKey.TimelineRange keys, got ${key::class}")
        val symbol =
            Currency.fromString(symbolIso)
                ?: error("Unknown currency ISO in timeline cache key: $symbolIso")
        return DiskJsonStore(rootDir, TimelineConverter(symbol))
    }
}
