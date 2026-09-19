package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.repository.cache.RateCache
import com.eliormachlev.currencix.repository.cache.RateCacheFactory
import com.eliormachlev.currencix.repository.cache.RateCacheKey
import com.eliormachlev.currencix.util.ApiHttpError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate

private const val MIN_UPDATE_DISPLAY_MS = 750L

// How far back to keep timeline data. The UI only renders the last year, but a
// small buffer avoids re-fetching when the sliding window shifts by a day.
private const val TIMELINE_WINDOW_DAYS = 400L

// Non-breaking space + 👀 emoji, used as the trailing eyeballs on the bold
// error message shown in the UI (rendered as HTML by the calling view).
private const val EYES_SUFFIX = "\u00A0\uD83D\uDC40"

// Non-breaking space + 🤓 emoji, used at the end of the "try another API"
// suggestion line.
private const val NERD_SUFFIX = "\u00A0\uD83E\uDD13"

class ExchangeRatesRepository(
    private val context: Context,
) {
    private val db = Database(context)
    private val liveExchangeRates = db.getExchangeRates()
    private val liveTimeline = MutableLiveData<Timeline?>()
    private var liveError = MutableLiveData<String?>()
    private var isUpdating = db.isUpdating()

    // In-house rate cache (#148): sits on top of the shared OkHttp Cache so
    // memory / disk tiers of *parsed* domain objects short-circuit the parse
    // pipeline when the freshness window still holds. Retry-with-jitter and
    // in-flight dedupe live inside the cache — this class no longer needs its
    // own dedupe map for the rates path.
    private val ratesCache: RateCache<RateCacheKey.RatesLatest, ExchangeRates> =
        RateCacheFactory.buildRatesCache(
            context = context,
            secretsSupplier = { ApiSecrets(openExchangeRatesApiKey = db.getOpenExchangeRatesApiKey()) },
        )
    private val timelineCache: RateCache<RateCacheKey.TimelineRange, Timeline> =
        RateCacheFactory.buildTimelineCache(context)

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
     * refires with the fresh value.
     */
    fun getExchangeRates(): LiveData<ExchangeRates?> {
        if (ratesJob?.isActive != true) {
            val provider = db.getApiProvider()
            val historicalDate = db.getHistoricalDate()
            val key =
                RateCacheKey.RatesLatest(
                    providerId = provider.id,
                    baseIso = provider.baseCurrencyIso(),
                    date = historicalDate,
                )
            ratesJob =
                launchApiCall { start ->
                    ratesCache
                        .get(key)
                        .map { it.copy(provider = provider) }
                        .processResponse(
                            start = start,
                            successFlag = { success },
                            errorMessage = { error },
                            onSuccess = { db.insertExchangeRates(it) },
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
    suspend fun refreshLatestRates(): Result<ExchangeRates> {
        val provider = db.getApiProvider()
        val historicalDate = db.getHistoricalDate()
        val key =
            RateCacheKey.RatesLatest(
                providerId = provider.id,
                baseIso = provider.baseCurrencyIso(),
                date = historicalDate,
            )
        return ratesCache
            .refresh(key)
            .map { it.copy(provider = provider) }
            .onSuccess { db.insertExchangeRates(it) }
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
    ): LiveData<Timeline?> {
        val key = timelineKey(base, symbol)
        // Record intent before any dedup check so rapid pair switches always
        // update the gate, even when the fetch itself is skipped due to an
        // in-flight job for the same pair.
        latestTimelineKey = key
        val existing = timelineJobs[key]
        if (existing?.isActive != true) {
            val provider = db.getApiProvider()
            val cached = db.getCachedTimeline(provider, base, symbol)
            // Fast-paint: show cached data while the tail refresh runs. Gated on
            // latestTimelineKey so a prefetch for a stale pair can't paint over
            // whatever the user is currently looking at.
            if (cached != null && isCurrentTimelinePair(key)) liveTimeline.postValue(cached)

            val today = LocalDate.now()
            val windowStart = today.minusDays(TIMELINE_WINDOW_DAYS)
            // Re-fetch the last cached day (in case it was preliminary) plus everything
            // after it. If we have no cache, fetch the full window.
            val fetchStart =
                cached
                    ?.rates
                    ?.keys
                    ?.lastOrNull()
                    ?.let { maxOf(it, windowStart) }
                    ?: windowStart

            val cacheKey =
                RateCacheKey.TimelineRange(
                    providerId = provider.id,
                    baseIso = base.iso4217Alpha(),
                    symbolIso = symbol.iso4217Alpha(),
                    startDate = fetchStart,
                    endDate = today,
                )

            val job =
                launchApiCall { start ->
                    // refresh() rather than get() — the repo does its own
                    // tail-merge against Database.getCachedTimeline, so
                    // serving a stale RateCache entry here would skip the
                    // missing-days fetch. We still benefit from RateCache's
                    // retry-with-jitter + in-flight dedupe on the upstream
                    // call itself.
                    timelineCache
                        .refresh(cacheKey)
                        .map { it.copy(provider = provider) }
                        .processResponse(
                            start = start,
                            successFlag = { success },
                            errorMessage = { error },
                            onSuccess = { fresh ->
                                val merged = mergeTimeline(cached, fresh, base, symbol, windowStart)
                                // Always persist — even for pairs the user has since navigated
                                // away from, so switching back fast-paints from fresh cache.
                                db.putCachedTimeline(merged, base, symbol)
                                // Only notify the UI if this pair is still the one the user cares about.
                                // postValue is safe from any thread — avoids spawning a
                                // fire-and-forget CoroutineScope just to hop to Main.
                                if (isCurrentTimelinePair(key)) {
                                    liveTimeline.postValue(merged)
                                }
                            },
                        )
                }
            timelineJobs[key] = job
        }
        return liveTimeline
    }

    private fun mergeTimeline(
        cached: Timeline?,
        fresh: Timeline,
        base: Currency,
        symbol: Currency,
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

    private fun launchApiCall(block: suspend (start: Long) -> Unit): Job {
        val start = System.currentTimeMillis()
        db.setUpdating(true)
        return CoroutineScope(Dispatchers.IO).launch { block(start) }
    }

    private suspend fun <T : Any> Result<T>.processResponse(
        start: Long,
        successFlag: T.() -> Boolean?,
        errorMessage: T.() -> String?,
        onSuccess: suspend (T) -> Unit,
    ) {
        val data = getOrNull()
        val error = exceptionOrNull()
        if (data != null && error == null) {
            val ok = data.successFlag()
            if (ok == null || ok == true) {
                postIsUpdating(start)
                onSuccess(data)
                liveError.postValue(null)
            } else {
                postError(data.errorMessage())
            }
        } else {
            handleGenericError(error)
        }
    }

    private fun handleGenericError(error: Throwable?) {
        when (error) {
            null ->
                postError(R.string.error_generic.text())
            // Non-2xx HTTP response
            is ApiHttpError ->
                postError(R.string.error_http.text(error.statusCode))
            // timeout after 15s. likely server not reachable
            is SocketTimeoutException ->
                postError(R.string.error_timeout.text())
            // happens e.g. when device is offline or there's a DNS error
            is UnknownHostException ->
                postError(R.string.error_no_data.text())
            // received no data - happens e.g. with RUB @ Norges Bank
            is NoSuchElementException ->
                postError(R.string.error_empty_response.text())
            // everything else
            else ->
                postError(
                    error.localizedMessage?.let { R.string.error.text(it) }
                        ?: R.string.error_generic.text(),
                )
        }
    }

    fun getError(): LiveData<String?> = liveError

    fun isUpdating(): LiveData<Boolean> = isUpdating

    /*
     * "update" for at least 750ms
     */
    private suspend fun postIsUpdating(start: Long) {
        val now = System.currentTimeMillis()
        if (now - start < MIN_UPDATE_DISPLAY_MS) {
            db.setUpdating(true)

            withContext(Dispatchers.Main) {
                launch {
                    delay(MIN_UPDATE_DISPLAY_MS - (now - start))
                    db.setUpdating(false)
                }
            }
        } else {
            db.setUpdating(false)
        }
    }

    private fun postError(message: String?) {
        // disable progress bar
        db.setUpdating(false)

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
        ApiProvider.BANK_OF_ISRAEL -> Currency.ILS.iso4217Alpha()
    }
