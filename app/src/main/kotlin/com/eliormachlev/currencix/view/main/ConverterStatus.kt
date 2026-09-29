package com.eliormachlev.currencix.view.main

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleOwner
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.NetworkStatusLiveData
import com.eliormachlev.currencix.util.fromHtmlLegacy
import com.eliormachlev.currencix.util.stripRtlMark
import com.eliormachlev.currencix.util.stripTimePattern
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
 * The converter's status line and error toasts. Tracks connectivity, the
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

    // True when the most recent refresh failed (5xx, timeout, DNS, …) while
    // the device was online. Cleared once a new rates payload arrives — a
    // successful update is the definitive "provider is back".
    private var lastRefreshFailed = false

    private var converterVisible = false
    private var pendingError: String? = null

    fun observe(owner: LifecycleOwner) {
        Database(context).getDateFormat().observe(owner) { pattern ->
            dateFormatPattern = pattern
            recompute()
        }
        viewModel.getExchangeRates().observe(owner) { rates ->
            latestRatesDate = rates?.date
            latestRatesTime = rates?.time
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
        converterVisible = visible
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
        Toast.makeText(context, message.fromHtmlLegacy(), Toast.LENGTH_LONG).show()
        // Only "provider unreachable" when the device itself is online —
        // otherwise the OFFLINE banner already tells the story.
        if (isOnline) {
            lastRefreshFailed = true
            recompute()
        }
    }

    // Ranking: Offline > Unreachable > Historical. Each condition subsumes
    // the "rates aren't fresh" signal of the next, so the most actionable
    // signal wins the pill.
    private fun recompute() {
        bannerState.value =
            when {
                !isOnline -> staleBanner(BannerKind.Offline, R.string.offline_banner_with_date, R.string.offline_banner_no_data)
                lastRefreshFailed ->
                    staleBanner(BannerKind.Unreachable, R.string.unreachable_banner_with_date, R.string.unreachable_banner_no_data)
                historicalDate != null ->
                    BannerContent(
                        BannerKind.Historical,
                        context.getString(R.string.historical_banner, formatTimestamp(historicalDate, null).orEmpty()),
                    )
                else -> null
            }
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
