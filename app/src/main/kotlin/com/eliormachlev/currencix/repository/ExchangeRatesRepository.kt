package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.alert.checkRateAlerts
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.adapter.NO_DATA_ERROR
import com.eliormachlev.currencix.repository.cache.RateCache
import com.eliormachlev.currencix.repository.cache.RateCacheFactory
import com.eliormachlev.currencix.repository.cache.RateCacheKey
import com.eliormachlev.currencix.util.ApiHttpError
import com.eliormachlev.currencix.util.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate

// The default timeline fetch: the last year, plus a small buffer so the
// sliding window shifting by a day doesn't re-fetch.
private const val TIMELINE_WINDOW_DAYS = 400L

// The furthest back a timeline goes (the 5-year view, and custom ranges);
// cached history older than this is dropped.
internal const val TIMELINE_MAX_YEARS = 10L

// A cached timeline "starts at" the requested date if its first day is within
// this of it: rates aren't published on weekends and holidays, so the first
// observation is often a few days after the requested start.
private const val HEAD_SLACK_DAYS = 7L

/** The default start of a timeline fetch: a year back, with a buffer. */
fun defaultTimelineSince(today: LocalDate = LocalDate.now()): LocalDate = today.minusDays(TIMELINE_WINDOW_DAYS)

// The longest stretch one timeline request asks for: a longer range comes
// in pieces, newest first, each shown as it arrives.
private const val TIMELINE_CHUNK_YEARS = 1L

/**
 * The date ranges a timeline fetch asks for, in order, given what's cached
 * ([cachedFirst] … [cachedLast]) and how far back it's wanted ([since]):
 * the tail from the last cached day (re-fetched, in case it was preliminary)
 * to [today], then whatever the cache lacks before its first day, a year at
 * a time going back. With nothing cached, the whole span a year at a time
 * from [today] back. A cached stretch is never asked for again, so a longer
 * period reuses what a shorter one fetched.
 */
internal fun timelineFetchRanges(
    since: LocalDate,
    cachedFirst: LocalDate?,
    cachedLast: LocalDate?,
    today: LocalDate,
): List<ClosedRange<LocalDate>> {
    if (cachedFirst == null || cachedLast == null) return yearlyBackFrom(today, since)
    val tail = listOf(maxOf(cachedLast, since)..today)
    val headMissing = cachedFirst.isAfter(since.plusDays(HEAD_SLACK_DAYS))
    return if (headMissing) tail + yearlyBackFrom(cachedFirst, since) else tail
}

// [end] back to [start] in ranges of at most TIMELINE_CHUNK_YEARS, newest
// first; a single day when they're the same.
private fun yearlyBackFrom(
    end: LocalDate,
    start: LocalDate,
): List<ClosedRange<LocalDate>> =
    generateSequence(end) { it.minusYears(TIMELINE_CHUNK_YEARS) }
        .takeWhile { it.isAfter(start) }
        .map { chunkEnd -> maxOf(start, chunkEnd.minusYears(TIMELINE_CHUNK_YEARS))..chunkEnd }
        .toList()
        .ifEmpty { listOf(start..end) }

// Non-breaking space + 👀 emoji, used as the trailing eyeballs on the bold
// error message shown in the UI (rendered as HTML by the calling view).
private const val EYES_SUFFIX = "\u00A0\uD83D\uDC40"

// Non-breaking space + 🤓 emoji, used at the end of the "try another API"
// suggestion line.
private const val NERD_SUFFIX = "\u00A0\uD83E\uDD13"

class ExchangeRatesRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers = AppDispatchers.production,
) {
    private val db = Database(context)
    private val liveExchangeRates = db.rates.getExchangeRates()
    private val liveTimeline = MutableLiveData<Timeline?>()
    private val liveError = MutableLiveData<String?>()

    // In-house rate cache (#148): sits on top of the shared OkHttp Cache so
    // memory / disk tiers of *parsed* domain objects short-circuit the parse
    // pipeline when the freshness window still holds. Retry-with-jitter and
    // in-flight dedupe live inside the cache — this class no longer needs its
    // own dedupe map for the rates path.
    private val ratesCache: RateCache<RateCacheKey.RatesLatest, ExchangeRates> =
        RateCacheFactory.buildRatesCache(
            context = context,
            secretsSupplier = db.providers::secrets,
        )
    private val timelineCache: RateCache<RateCacheKey.TimelineRange, Timeline> =
        RateCacheFactory.buildTimelineCache(context, secretsSupplier = db.providers::secrets)

    // Track in-flight fetches so rapid re-triggers (swipe-to-refresh spam,
    // toolbar tap during pull, currency swap chains) share the current job
    // instead of spawning parallel HTTP requests against the same endpoint.
    // A single key per fetch shape is enough: rates has no args, timeline
    // is keyed by "base|symbol".
    private var ratesJob: Job? = null
    private val timelineJobs = mutableMapOf<String, Job>()

    // The pair the user most recently requested. Timeline fetches for any other
    // pair still complete and populate the on-disk cache, but their result is
    // not posted to [liveTimeline] — otherwise a stale prefetch finishing after
    // the user switched currencies would clobber the visible chart. Volatile
    // because it's written from Main and read from IO in the fetch callback.
    @Volatile private var latestTimelineKey: String? = null

    private fun timelineKey(
        base: Currency,
        symbol: Currency,
    ): String = "${base.iso4217Alpha()}|${symbol.iso4217Alpha()}"

    private fun isCurrentTimelinePair(key: String): Boolean = key == latestTimelineKey

    /**
     * Gets and returns all latest exchange rates from the API.
     *
     * The actual fetch runs through the in-house rate cache (memory → disk →
     * upstream with retry-with-jitter and in-flight dedupe). LiveData is
     * still sourced from DataStore so existing UI subscribers keep working
     * unchanged; a cache-hit still writes to DataStore so the LiveData
     * refires with the fresh value. [fresh] (pull to refresh, back online)
     * goes past the cache's freshness window and asks the server whether its
     * cached answer still holds.
     */
    fun getExchangeRates(fresh: Boolean = false): LiveData<ExchangeRates?> {
        if (ratesJob?.isActive != true) {
            ratesJob =
                launchApiCall {
                    fetchRates(fresh).processResponse(
                        successFlag = { success },
                        errorMessage = { error },
                        onSuccess = ::store,
                    )
                }
        }
        return liveExchangeRates
    }

    /**
     * Suspend-only refresh path used by the WorkManager auto-refresh job
     * (#151). Bypasses cache freshness via [RateCache.refresh], re-populates
     * both cache tiers, and persists the result via [Database.insertExchangeRates]
     * so LiveData subscribers (Compose UI, widget) see the fresh values on
     * next launch. Returns a [Result] so the worker can distinguish
     * transient network errors (→ retry) from success.
     *
     * Does *not* touch the "is updating" flag: this runs in the background
     * with no UI visible, and the spinner in the hero card would flash on
     * next foregrounding for no user-facing reason.
     */
    suspend fun refreshLatestRates(): Result<ExchangeRates> = fetchRates(fresh = true).onSuccess(::store)

    // New rates kept for the app and the widget, then checked against the rate alerts.
    private fun store(rates: ExchangeRates) {
        db.rates.insertExchangeRates(rates)
        checkRateAlerts(context, db, rates)
    }

    // The main provider's rates; if it fails while online, the fallback's
    // (Database.getFallbackProvider), marked with the main provider they
    // stand in for. When both fail, the main provider's failure is the one
    // reported. [fresh] bypasses the cache's freshness window.
    //
    // Both are asked at once, so when the main provider fails the fallback's
    // answer is already there (or on its way) instead of only starting then.
    // When the main one answers, its rates are used and the fallback's fetch
    // finishes in the rate cache's own scope, keeping that cache warm.
    private suspend fun fetchRates(fresh: Boolean): Result<ExchangeRates> =
        coroutineScope {
            val main = db.providers.apiProvider.get()
            val backup = db.providers.fallbackProvider.get()
            val fallback = if (backup == main) null else async { loadRates(backup, fresh).map { it.copy(fallbackFrom = main) } }
            val result = loadRates(main, fresh)
            if (fallback == null || !result.shouldTryFallback { success }) {
                fallback?.cancel()
                return@coroutineScope result
            }
            val answer = fallback.await()
            if (answer.isUsable { success }) answer else result
        }

    private suspend fun loadRates(
        provider: ApiProvider,
        fresh: Boolean,
    ): Result<ExchangeRates> {
        val key =
            RateCacheKey.RatesLatest(
                providerId = provider.id,
                baseIso = provider.baseCurrencyIso(),
                date = db.lastState.historicalDate.get(),
            )
        val result = if (fresh) ratesCache.refresh(key) else ratesCache.get(key)
        return result.map { it.copy(provider = provider) }
    }

    /**
     * Gets and returns the timeline of the last year of the given base and target currency.
     *
     * Persists per-pair timelines and refreshes only the missing tail on each call, so
     * repeated opens paint instantly from cache and hit the network only for the days
     * that were added since the last fetch.
     */
    fun getTimeline(
        base: Currency,
        symbol: Currency,
        since: LocalDate = defaultTimelineSince(),
    ): LiveData<Timeline?> {
        val key = timelineKey(base, symbol)
        val start = maxOf(since, LocalDate.now().minusYears(TIMELINE_MAX_YEARS))
        // Record intent before any dedup check so rapid pair switches always
        // update the gate, even when the fetch itself is skipped due to an
        // in-flight job for the same pair.
        latestTimelineKey = key
        // A fetch reaching further back isn't covered by one already running.
        val jobKey = "$key|$start"
        val existing = timelineJobs[jobKey]
        if (existing?.isActive != true) {
            val main = db.providers.apiProvider.get()
            // Fast-paint: show cached data while the tail refresh runs. Gated on
            // latestTimelineKey so a prefetch for a stale pair can't paint over
            // whatever the user is currently looking at.
            val cached = db.rates.getCachedTimeline(main, base, symbol)
            if (cached != null && isCurrentTimelinePair(key)) liveTimeline.postValue(cached)

            val job =
                launchApiCall {
                    // Main provider first; the fallback when it fails while
                    // online (its own cached window, not merged with the main's).
                    val showPartial: (Timeline) -> Unit = { partial -> if (isCurrentTimelinePair(key)) liveTimeline.postValue(partial) }
                    var result = fetchTimeline(main, base, symbol, start, showPartial)
                    if (result.shouldTryFallback { success }) {
                        val fallback = fetchTimeline(db.providers.fallbackProvider.get(), base, symbol, start, showPartial)
                        if (fallback.isUsable { success }) result = fallback
                    }
                    result.processResponse(
                        successFlag = { success },
                        errorMessage = { error },
                        onSuccess = { merged ->
                            // Only notify the UI if this pair is still the one the user cares about.
                            // postValue is safe from any thread — avoids spawning a
                            // fire-and-forget CoroutineScope just to hop to Main.
                            if (isCurrentTimelinePair(key)) liveTimeline.postValue(merged)
                        },
                    )
                }
            timelineJobs[jobKey] = job
        }
        return liveTimeline
    }

    // [provider]'s timeline for the pair: the missing ranges fetched
    // (timelineFetchRanges) and merged into its cached window, which is
    // persisted after each one — even for pairs the user has since navigated
    // away from, so switching back fast-paints from fresh cache. [onPartial]
    // gets the window after each range but the last. The result is the
    // merged window; a range that fails ends the fetch, with what came
    // before it kept.
    private suspend fun fetchTimeline(
        provider: ApiProvider,
        base: Currency,
        symbol: Currency,
        since: LocalDate,
        onPartial: (Timeline) -> Unit = {},
    ): Result<Timeline> {
        var window = db.rates.getCachedTimeline(provider, base, symbol)
        val today = LocalDate.now()
        val cachedDates = window?.rates?.keys?.sorted()
        val ranges = timelineFetchRanges(since, cachedDates?.firstOrNull(), cachedDates?.lastOrNull(), today)
        var fetched: Result<Timeline>? = null
        for ((index, range) in ranges.withIndex()) {
            val result = fetchTimelineRange(provider, base, symbol, range)
            val fresh = result.getOrNull()
            if (fresh == null || fresh.success == false) {
                // Only the first range's failure is the fetch's: past it, the
                // pieces already merged are worth showing.
                if (fetched == null) return result.map { it.copy(provider = provider) }
                break
            }
            window =
                mergeTimeline(
                    window,
                    fresh,
                    base,
                    today.minusYears(TIMELINE_MAX_YEARS),
                ).also { db.rates.putCachedTimeline(it, base, symbol) }
            fetched = Result.success(window)
            if (index < ranges.lastIndex) onPartial(window)
        }
        // timelineFetchRanges always gives at least one range, so a fetch either
        // failed on the first (returned above) or merged something.
        return checkNotNull(fetched)
    }

    // One range of [provider]'s timeline. refresh() rather than get(): the
    // merge against the cached window needs the days asked for, which a
    // stale RateCache entry would skip. RateCache still adds
    // retry-with-jitter and in-flight dedupe.
    private suspend fun fetchTimelineRange(
        provider: ApiProvider,
        base: Currency,
        symbol: Currency,
        range: ClosedRange<LocalDate>,
    ): Result<Timeline> {
        val cacheKey =
            RateCacheKey.TimelineRange(
                providerId = provider.id,
                baseIso = base.iso4217Alpha(),
                symbolIso = symbol.iso4217Alpha(),
                startDate = range.start,
                endDate = range.endInclusive,
            )
        return timelineCache.refresh(cacheKey).map { it.copy(provider = provider) }
    }

    private fun mergeTimeline(
        cached: Timeline?,
        fresh: Timeline,
        base: Currency,
        windowStart: LocalDate,
    ): Timeline {
        val merged = sortedMapOf<LocalDate, Rate>()
        cached?.rates?.forEach { (date, rate) ->
            if (!date.isBefore(windowStart)) merged[date] = rate
        }
        // Fresh values overwrite cached values for the same date.
        fresh.rates?.forEach { (date, rate) -> merged[date] = rate }
        return Timeline(
            success = merged.isNotEmpty(),
            error = if (merged.isEmpty()) fresh.error else null,
            base = base.iso4217Alpha(),
            startDate = merged.keys.firstOrNull(),
            endDate = merged.keys.lastOrNull(),
            rates = merged,
            provider = fresh.provider ?: cached?.provider,
        )
    }

    // Worth asking the fallback: the main provider failed, or answered with
    // an error, while the device is online (offline, every provider fails).
    private fun <T> Result<T>.shouldTryFallback(successFlag: T.() -> Boolean?): Boolean =
        !isUsable(successFlag) && exceptionOrNull() !is UnknownHostException

    private fun <T> Result<T>.isUsable(successFlag: T.() -> Boolean?): Boolean = getOrNull()?.successFlag() != false && isSuccess

    private fun launchApiCall(block: suspend () -> Unit): Job {
        RefreshState.start()
        return CoroutineScope(dispatchers.io).launch { block() }
    }

    private suspend fun <T : Any> Result<T>.processResponse(
        successFlag: T.() -> Boolean?,
        errorMessage: T.() -> String?,
        onSuccess: suspend (T) -> Unit,
    ) {
        val data = getOrNull()
        val error = exceptionOrNull()
        if (data != null && error == null) {
            val ok = data.successFlag()
            if (ok == null || ok == true) {
                RefreshState.finish()
                onSuccess(data)
                liveError.postValue(null)
            } else {
                postError(providerError(data.errorMessage()))
            }
        } else {
            handleGenericError(error)
        }
    }

    // A provider's error in the app's language where it's one of ours (an
    // empty answer); anything else is the server's own words, shown as sent.
    private fun providerError(message: String?): String? = if (message == NO_DATA_ERROR) R.string.error_empty_response.text() else message

    private fun handleGenericError(error: Throwable?) {
        when (error) {
            null -> {
                postError(R.string.error_generic.text())
            }

            // Non-2xx HTTP response
            is ApiHttpError -> {
                postError(R.string.error_http.text(error.statusCode))
            }

            // timeout after 15s. likely server not reachable
            is SocketTimeoutException -> {
                postError(R.string.error_timeout.text())
            }

            // happens e.g. when device is offline or there's a DNS error
            is UnknownHostException -> {
                postError(R.string.error_no_data.text())
            }

            // received no data - happens e.g. with RUB @ Norges Bank
            is NoSuchElementException -> {
                postError(R.string.error_empty_response.text())
            }

            // everything else
            else -> {
                postError(
                    error.localizedMessage?.let { R.string.error.text(it) }
                        ?: R.string.error_generic.text(),
                )
            }
        }
    }

    fun getError(): LiveData<String?> = liveError

    private fun postError(message: String?) {
        // disable progress bar
        RefreshState.finish()

        // post error
        var errorMessage = "<b>" + (message ?: R.string.error_api_error.text()) + "$EYES_SUFFIX</b>"
        // tell the user the API can be changed
        if (message?.contains(R.string.error_no_data.text()) != true) {
            errorMessage += "\n<br>${R.string.error_try_another_api.text()}$NERD_SUFFIX"
        }
        liveError.postValue(errorMessage)
        // Preserve any cached/previously-fetched timeline instead of wiping it —
        // showing stale-but-valid data beats a blank chart when the network hiccups.
    }

    private fun Int.text(vararg message: Any): String = context.getString(this, *message)
}

// Best-effort base-currency ISO for each provider. Used to key the in-house
// [RateCache] entry — a provider swap flips the key so cached rates never
// leak across providers. Nullable-safe: providers whose base is unknown fall
// through to an empty string, still producing a distinct-per-provider key.
private fun ApiProvider.baseCurrencyIso(): String =
    when (this) {
        ApiProvider.FRANKFURTER_APP,
        ApiProvider.INFOR_EURO,
        -> Currency.EUR.iso4217Alpha()

        ApiProvider.NORGES_BANK -> Currency.NOK.iso4217Alpha()

        ApiProvider.BANK_ROSSII -> Currency.RUB.iso4217Alpha()

        ApiProvider.BANK_OF_CANADA -> Currency.CAD.iso4217Alpha()

        ApiProvider.OPEN_EXCHANGERATES -> Currency.USD.iso4217Alpha()

        ApiProvider.ALL_RATES_TODAY -> Currency.EUR.iso4217Alpha()

        ApiProvider.BANK_OF_ISRAEL -> Currency.ILS.iso4217Alpha()
    }
