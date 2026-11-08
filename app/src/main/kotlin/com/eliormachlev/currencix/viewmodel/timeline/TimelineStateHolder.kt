package com.eliormachlev.currencix.viewmodel.timeline

import android.app.Application
import androidx.compose.runtime.retain.RetainObserver
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.repository.ExchangeRatesRepository
import com.eliormachlev.currencix.repository.RefreshState
import com.eliormachlev.currencix.repository.TIMELINE_MAX_YEARS
import com.eliormachlev.currencix.repository.defaultTimelineSince
import com.eliormachlev.currencix.util.AppDispatchers
import com.eliormachlev.currencix.util.calculateDifference
import com.eliormachlev.currencix.util.getSignificantDecimalPlaces
import com.eliormachlev.currencix.viewmodel.util.RetainedScope
import com.eliormachlev.currencix.viewmodel.util.stateInWhileSubscribed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import kotlin.math.min

private const val DEFAULT_DECIMAL_PLACES = 3
private const val SIGNIFICANT_DIGITS = 3
private const val MAX_DECIMAL_PLACES = 7

// Restrict a set of (date -> rate) entries to those on/after the user's scrub
// point on the timeline. When [scrubDate] is null, no filtering happens.
// The entry the change is measured from: the scrubbed day, or the range's first.
private fun Set<Map.Entry<LocalDate, Rate?>>.pastEntry(scrubDate: LocalDate?): Map.Entry<LocalDate, Rate?>? =
    if (scrubDate != null) find { it.key == scrubDate } else firstOrNull()

private fun Set<Map.Entry<LocalDate, Rate?>>.fromScrub(scrubDate: LocalDate?): List<Map.Entry<LocalDate, Rate?>> =
    if (scrubDate == null) {
        this.toList()
    } else {
        this.filter { !it.key.isBefore(scrubDate) }
    }

private val TWO = BigDecimal(2)

private fun averageOf(values: List<BigDecimal>): BigDecimal =
    values
        .fold(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal(values.size), MathContext.DECIMAL128)

private fun medianOf(values: List<BigDecimal>): BigDecimal {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[mid]
    } else {
        sorted[mid - 1].add(sorted[mid]).divide(TWO, MathContext.DECIMAL128)
    }
}

/** The timeline screen's state for [base] → [target], kept with `retain`. */
class TimelineStateHolder(
    private val app: Application,
    private var base: Currency,
    private var target: Currency,
    private val dispatchers: AppDispatchers = AppDispatchers.production,
    private val retained: RetainedScope = RetainedScope(dispatchers),
) : RetainObserver by retained {
    private val scope = retained.scope

    enum class Period {
        WEEK,
        MONTH,
        YEAR,
        FIVE_YEARS,

        /** The user's own dates ([setCustomRange]). */
        CUSTOM,
        ;

        /** Where this period starts, ending [today]; null for [CUSTOM], which has its own dates. */
        fun startDate(today: LocalDate = LocalDate.now()): LocalDate? =
            when (this) {
                WEEK -> today.minusWeeks(1)
                MONTH -> today.minusMonths(1)
                YEAR -> today.minusYears(1)
                FIVE_YEARS -> today.minusYears(FIVE)
                CUSTOM -> null
            }

        private companion object {
            const val FIVE = 5L
        }
    }

    /** The dates a [Period] covers: [start] to [end] (today, unless custom). */
    data class Span(
        val start: LocalDate,
        val end: LocalDate,
    )

    private val repository: ExchangeRatesRepository = ExchangeRatesRepository(app)

    private val period = MutableStateFlow(Period.YEAR)
    private val periodState: StateFlow<Period> = period.asStateFlow()
    private val customRange = MutableStateFlow<Span?>(null)
    private val customRangeState: StateFlow<Span?> = customRange.asStateFlow()

    // How far back this screen has asked the repository for — a longer span
    // fetches further back; a shorter one filters what's already there.
    private var fetchedSince: LocalDate = defaultTimelineSince()

    // currently selected date
    private val scrubDate = MutableStateFlow<LocalDate?>(null)

    // error
    private val error = repository.getError()

    // updating — see RefreshState: the chart's progress bar is a passive
    // indicator; menu enablement is logic, so it reads the raw state.
    private val refreshIndicator: StateFlow<Boolean> = RefreshState.passiveIndicator.stateInWhileSubscribed(scope, false)

    // The timeline for the dates on screen. Asks the repository for it on
    // first use; null until it arrives.
    private val dbItems: StateFlow<Timeline?> by lazy {
        combine(repository.getTimeline(base, target, fetchedSince), period, customRange) { timeline, _, _ ->
            val span = currentSpan()
            timeline?.let { fetched ->
                fetched.copy(
                    startDate = span.start,
                    rates = fetched.rates?.filterKeys { !it.isBefore(span.start) && !it.isAfter(span.end) },
                )
            }
        }.stateInWhileSubscribed(scope, null)
    }

    /** The dates on screen: the chosen period's, or the custom range. */
    private fun currentSpan(today: LocalDate = LocalDate.now()): Span =
        period.value.startDate(today)?.let { Span(it, today) }
            ?: customRange.value
            ?: Span(today.minusYears(1), today)

    // Asks for older history when the span on screen starts before what's
    // been fetched; a span within it needs no network.
    private fun fetchCovering(span: Span) {
        if (!span.start.isBefore(fetchedSince)) return
        fetchedSince = span.start
        repository.requestTimeline(base, target, fetchedSince)
    }

    /*
     * getters for the various values ==============================================================
     */

    /**
     * The pair on screen, for the title. Follows [toggleCurrencies]; null
     * until the first timeline arrives, so the title doesn't flash the pair
     * over an empty chart.
     */
    fun getCurrencyPair(): StateFlow<Pair<Currency, Currency>?> = currencyPairState

    // Each readout is one StateFlow for the screen's life, made on first use:
    // the screen asks for them while it composes, and a fresh one per
    // recomposition (several a second while the chart is scrubbed) redid the
    // whole computation each time.
    private val currencyPairState by lazy { fromItems { if (it == null) null else base to target } }

    /** Fetches the pair again — after an error, or once the provider changed. */
    fun retry() {
        repository.requestTimeline(base, target, fetchedSince)
    }

    fun toggleCurrencies() = setCurrencies(target, base)

    /**
     * Charts [base] → [target] instead (a currency picked on a pill, or the
     * pair swapped): the timeline follows, as far back as this screen has
     * fetched.
     */
    fun setCurrencies(
        base: Currency,
        target: Currency,
    ) {
        if (base == this.base && target == this.target) return
        this.base = base
        this.target = target
        repository.requestTimeline(base, target, fetchedSince)
    }

    fun getProvider(): StateFlow<CharSequence?> = providerState

    private val providerState by lazy { fromItems { it?.provider?.getName(app) } }

    fun getRates(): StateFlow<Map<LocalDate, Rate>?> = ratesState

    private val ratesState by lazy { fromItems { it?.rates } }

    // [transform] of the timeline on screen, following it; null until first observed.
    private fun <T> fromItems(transform: (Timeline?) -> T?): StateFlow<T?> =
        dbItems.map { transform(it) }.stateInWhileSubscribed(scope, null)

    /**
     * True when the dates on screen hold no rates once loading is done — a
     * custom range over a weekend, or before the provider's history. While
     * older history is still downloading it stays false (the progress bar
     * says what's happening).
     */
    fun isRangeEmpty(): StateFlow<Boolean> = rangeEmptyState

    private val rangeEmptyState: StateFlow<Boolean> by lazy {
        combine(dbItems, RefreshState.inFlight) { timeline, loading -> !loading && timeline?.rates?.isEmpty() == true }
            .stateInWhileSubscribed(scope, false)
    }

    fun getRateCurrent(): StateFlow<RateReadout?> = rateCurrentState

    private val rateCurrentState by lazy {
        rangeState(followScrub = false) { rates, _, decimals -> rates?.lastOrNull().toReadout(decimals) }
    }

    fun getRatePast(): StateFlow<RateReadout?> = ratePastState

    private val ratePastState by lazy { rangeState { rates, scrubDate, decimals -> rates?.pastEntry(scrubDate).toReadout(decimals) } }

    fun getRatesDifferencePercent(): StateFlow<BigDecimal?> = differenceState

    private val differenceState by lazy {
        rangeState(withDecimals = false) { rates, scrubDate, _ ->
            calculateDifference(rates?.pastEntry(scrubDate)?.value?.value, rates?.lastOrNull()?.value?.value)
        }
    }

    fun getRatesAverage(): StateFlow<RateReadout?> = averageState

    private val averageState by lazy { aggregateState(::averageOf) }

    fun getRatesMedian(): StateFlow<RateReadout?> = medianState

    private val medianState by lazy { aggregateState(::medianOf) }

    // Range-wide statistics that reduce the visible range's values to one
    // [Rate]. The reducer receives a non-empty list; an empty range
    // short-circuits to null before it runs.
    private fun aggregateState(reducer: (List<BigDecimal>) -> BigDecimal): StateFlow<RateReadout?> =
        rangeState { rates, scrubDate, decimals ->
            val values = rates?.fromScrub(scrubDate)?.mapNotNull { entry -> entry.value?.value }
            RateReadout(values?.takeIf { it.isNotEmpty() }?.let { Rate(target, reducer(it)) }, date = null, decimals)
        }

    fun getRatesMin(): StateFlow<RateReadout?> = minState

    private val minState by lazy { rangeState { rates, scrubDate, decimals -> extremeOf(rates, scrubDate, decimals, pickMax = false) } }

    fun getRatesMax(): StateFlow<RateReadout?> = maxState

    private val maxState by lazy { rangeState { rates, scrubDate, decimals -> extremeOf(rates, scrubDate, decimals, pickMax = true) } }

    // Range-wide extremes for the on-chart reference lines: intentionally
    // ignore the scrub position so the horizontal min/max lines stay anchored
    // to the visible period's absolute extremes while the finger drags.
    fun getRatesRangeMin(): StateFlow<Double?> = rangeMinState

    private val rangeMinState by lazy { rangeExtreme(pickMax = false) }

    fun getRatesRangeMax(): StateFlow<Double?> = rangeMaxState

    private val rangeMaxState by lazy { rangeExtreme(pickMax = true) }

    // The extreme's value alone, which the decimal places don't change.
    private fun rangeExtreme(pickMax: Boolean): StateFlow<Double?> =
        rangeState(followScrub = false, withDecimals = false) { rates, _, decimals ->
            extremeOf(rates, scrubDate = null, decimals, pickMax).rate?.value?.toDouble()
        }

    // The lowest (or, with [pickMax], highest) rate from [scrubDate] on, and the last day it was seen.
    private fun extremeOf(
        rates: Set<Map.Entry<LocalDate, Rate?>>?,
        scrubDate: LocalDate?,
        decimals: Int,
        pickMax: Boolean,
    ): RateReadout {
        val slice = rates?.fromScrub(scrubDate)
        val extreme: Rate? =
            slice
                ?.mapNotNull { entry -> entry.value }
                ?.let { if (pickMax) it.maxByOrNull { r -> r.value } else it.minByOrNull { r -> r.value } }
                ?.let { Rate(target, it.value) }
        val date: LocalDate? =
            slice
                ?.findLast { entry -> entry.value?.value?.compareTo(extreme?.value) == 0 }
                ?.key
        return RateReadout(extreme, date, decimals)
    }

    /**
     * A value [compute]d from the range's rates, the scrub date and the
     * decimal places, redone when the rates change, the scrub date moves
     * ([followScrub]) or the decimal places do ([withDecimals]). Null until
     * the first one is worked out.
     *
     * Computed off the main thread: dragging along the chart moves the scrub
     * date every frame, and the median sorts up to ten years of rates. Only
     * the latest request's result is shown; one overtaken is dropped.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> rangeState(
        followScrub: Boolean = true,
        withDecimals: Boolean = true,
        compute: (rates: Set<Map.Entry<LocalDate, Rate?>>?, scrubDate: LocalDate?, decimals: Int) -> T,
    ): StateFlow<T?> =
        combine(
            dbItems,
            if (followScrub) scrubDate else flowOf(null),
            if (withDecimals) decimalPlaces else flowOf(DEFAULT_DECIMAL_PLACES),
        ) { timeline, scrub, decimals -> RangeInput(timeline?.rates?.entries, scrub, decimals) }
            .mapLatest { input -> withContext(dispatchers.default) { compute(input.rates, input.scrubDate, input.decimals) } }
            .stateInWhileSubscribed(scope, null)

    // One for all the readouts (each used to scan the rates for its own).
    private val decimalPlaces: StateFlow<Int> by lazy {
        dbItems.map(::decimalPlacesOf).stateInWhileSubscribed(scope, decimalPlacesOf(null))
    }

    fun getPeriod(): StateFlow<Period> = periodState

    /** The dates on screen right now (for the chart's share caption). */
    fun span(): Span = currentSpan()

    fun getCustomRange(): StateFlow<Span?> = customRangeState

    /** A preset period; [Period.CUSTOM] goes through [setCustomRange]. */
    fun setTimePeriod(period: Period) {
        if (period == Period.CUSTOM && customRange.value == null) return
        this.period.value = period
        fetchCovering(currentSpan())
    }

    /** Shows [start]…[end] (clamped to what the app keeps: [TIMELINE_MAX_YEARS]). */
    fun setCustomRange(
        start: LocalDate,
        end: LocalDate,
    ) {
        val today = LocalDate.now()
        val from = maxOf(minOf(start, end), today.minusYears(TIMELINE_MAX_YEARS))
        val to = minOf(maxOf(start, end), today)
        customRange.value = Span(from, to)
        period.value = Period.CUSTOM
        fetchCovering(Span(from, to))
    }

    fun setPastDate(date: LocalDate?) {
        scrubDate.value = date
    }

    /*
     * error =======================================================================================
     */

    /** The latest refresh's error message, null once one succeeds. */
    fun getError(): SharedFlow<String?> = error

    /** Chart progress bar: only for a refresh slow enough to notice. */
    fun isRefreshing(): StateFlow<Boolean> = refreshIndicator

    /** Raw "a refresh is running" — for enabling actions, not for display. */
    fun isRefreshInFlight(): StateFlow<Boolean> = RefreshState.inFlight
}

// What a readout is worked out from: the range's rates, the scrub date and the decimal places.
private class RangeInput(
    val rates: Set<Map.Entry<LocalDate, Rate?>>?,
    val scrubDate: LocalDate?,
    val decimals: Int,
)

// The decimal places the readouts show for [timeline]: enough significant
// digits for its range of rates.
private fun decimalPlacesOf(timeline: Timeline?): Int {
    val min = timeline?.rates?.entries?.minOfOrNull { rate -> rate.value.value } ?: BigDecimal.ZERO
    val max = timeline?.rates?.entries?.maxOfOrNull { rate -> rate.value.value } ?: BigDecimal.ZERO
    return min((min - max).abs().getSignificantDecimalPlaces(SIGNIFICANT_DIGITS), MAX_DECIMAL_PLACES)
}
