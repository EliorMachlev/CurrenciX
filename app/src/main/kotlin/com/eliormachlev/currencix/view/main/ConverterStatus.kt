package com.eliormachlev.currencix.view.main

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleOwner
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.NetworkStatusLiveData
import com.eliormachlev.currencix.util.fromHtmlLegacy
import com.eliormachlev.currencix.util.stripRtlMark
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.view.compose.AppSnackbar
import com.eliormachlev.currencix.view.main.compose.BannerContent
import com.eliormachlev.currencix.view.main.compose.BannerKind
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Date pattern used until the user-configured one arrives from DataStore.
internal const val DEFAULT_DATE_PATTERN = "dd/MM/yy HH:mm"

/**
 * Formats a rates timestamp with the user's [pattern]. With no [time], the
 * time part is stripped from the pattern first so a date-only provider
 * doesn't show a trailing "00:00". RTL marks some locale formatters inject
 * are removed so a right-aligned timestamp stays flush.
 */
internal fun formatRatesTimestamp(
    pattern: String,
    date: LocalDate?,
    time: LocalTime?,
): String? {
    if (date == null) return null
    val effective = if (time != null) pattern else stripTimePattern(pattern)
    val temporal = if (time != null) date.atTime(time) else date
    return DateTimeFormatter.ofPattern(effective).format(temporal).stripRtlMark()
}

/**
 * The converter's status line and error messages. Tracks connectivity, the
 * latest rates, the pinned historical date and refresh failures, and ranks
 * them into the one [banner] the hero card's rate footer shows.
 *
 * Observes for the Activity's whole lifetime, but only reports errors while
 * the converter is on screen ([setConverterVisible]): an error that arrives
 * while another screen covers it is held and shown on return — the same
 * thing the converter's Activity saw back when every screen was its own
 * Activity and LiveData paused it in the background.
 */
class ConverterStatus(
    private val context: Context,
    private val viewModel: MainViewModel,
    private val snackbar: AppSnackbar,
) {
    private val bannerState = mutableStateOf<BannerContent?>(null)
    val banner: State<BannerContent?> get() = bannerState

    /** The user's date pattern, kept for the share text as well as the banner. */
    var dateFormatPattern: String = DEFAULT_DATE_PATTERN
        private set

    private var isOnline = true
    private var latestRatesDate: LocalDate? = null
    private var latestRatesTime: LocalTime? = null
    private var historicalDate: LocalDate? = null

    // Set while the rates on screen came from the fallback provider:
    // (main provider that failed, fallback that answered).
    private var fallback: Pair<ApiProvider, ApiProvider>? = null

    // True when the most recent refresh failed (5xx, timeout, DNS, …) while
    // the device was online. Cleared once a new rates payload arrives — a
    // successful update is the definitive "provider is back".
    private var lastRefreshFailed = false

    // How many compositions of the converter are on screen. Usually 0 or 1,
    // but moving between the single and two-pane layouts briefly composes it
    // in both, and the old one leaving mustn't mark it hidden.
    private var visibleConverters = 0
    private val converterVisible get() = visibleConverters > 0
    private var pendingError: String? = null

    fun observe(owner: LifecycleOwner) {
        Database(context).display.getDateFormat().observe(owner) { pattern ->
            dateFormatPattern = pattern
            recompute()
        }
        viewModel.getExchangeRates().observe(owner) { rates ->
            latestRatesDate = rates?.date
            latestRatesTime = rates?.time
            fallback = rates?.fallbackFrom?.let { main -> rates.provider?.let { main to it } }
            if (rates != null) lastRefreshFailed = false
            recompute()
        }
        viewModel.getHistoricalLiveDate().observe(owner) { date ->
            historicalDate = date
            recompute()
        }
        viewModel.getError().observe(owner) { message ->
            if (converterVisible) onError(message) else pendingError = message
        }
        NetworkStatusLiveData(context).observe(owner) { online ->
            isOnline = online
            recompute()
        }
    }

    fun setConverterVisible(visible: Boolean) {
        visibleConverters = (visibleConverters + if (visible) 1 else -1).coerceAtLeast(0)
        if (visible) {
            pendingError?.let(::onError)
            pendingError = null
        }
    }

    fun formatTimestamp(
        date: LocalDate?,
        time: LocalTime?,
    ): String? = formatRatesTimestamp(dateFormatPattern, date, time)

    private fun onError(message: String?) {
        if (message.isNullOrEmpty()) return
        snackbar.show(message.fromHtmlLegacy())
        // Only "provider unreachable" when the device itself is online —
        // otherwise the OFFLINE banner already tells the story.
        if (isOnline) {
            lastRefreshFailed = true
            recompute()
        }
    }

    // Ranking: Offline > Unreachable > Fallback > Historical. Each condition subsumes
    // the "rates aren't fresh" signal of the next, so the most actionable
    // signal wins the pill.
    private fun recompute() {
        val fallback = fallback
        bannerState.value =
            when {
                !isOnline -> staleBanner(BannerKind.Offline, R.string.offline_banner_with_date, R.string.offline_banner_no_data)
                lastRefreshFailed ->
                    staleBanner(BannerKind.Unreachable, R.string.unreachable_banner_with_date, R.string.unreachable_banner_no_data)
                fallback != null -> fallbackBanner(fallback)
                historicalDate != null ->
                    BannerContent(
                        BannerKind.Historical,
                        context.getString(R.string.historical_banner, formatTimestamp(historicalDate, null).orEmpty()),
                    )
                else -> null
            }
    }

    // "Bank of Israel unavailable • Using Frankfurter.app", and what that
    // means for the tap on it.
    private fun fallbackBanner(providers: Pair<ApiProvider, ApiProvider>): BannerContent {
        val main = providers.first.getName(context)
        val used = providers.second.getName(context)
        return BannerContent(
            kind = BannerKind.Fallback,
            text = context.getString(R.string.fallback_banner, main, used),
            explanation = context.getString(R.string.fallback_info_message, main, used),
        )
    }

    // Offline and unreachable read the same way: "…, last updated <date>",
    // or a no-data variant before any rates ever arrived.
    private fun staleBanner(
        kind: BannerKind,
        withDateRes: Int,
        noDataRes: Int,
    ): BannerContent {
        val date = latestRatesDate
        val text =
            if (date != null) {
                context.getString(withDateRes, formatTimestamp(date, latestRatesTime).orEmpty())
            } else {
                context.getString(noDataRes)
            }
        return BannerContent(kind, text)
    }
}
