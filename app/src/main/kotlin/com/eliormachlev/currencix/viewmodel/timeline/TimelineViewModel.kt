package com.eliormachlev.currencix.viewmodel.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.repository.ExchangeRatesRepository
import com.eliormachlev.currencix.repository.RefreshState
import com.eliormachlev.currencix.repository.TIMELINE_MAX_YEARS
import com.eliormachlev.currencix.repository.defaultTimelineSince
import com.eliormachlev.currencix.util.calculateDifference
import com.eliormachlev.currencix.util.getSignificantDecimalPlaces
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import kotlin.math.min

private const val DEFAULT_DECIMAL_PLACES = 3
private const val SIGNIFICANT_DIGITS = 3
private const val MAX_DECIMAL_PLACES = 7

// Restrict a set of (date -> rate) entries to those on/after the user's scrub
// point on the timeline. When [scrubDate] is null, no filtering happens.
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

class TimelineViewModel(
    private val app: Application,
    private var base: Currency,
    private var target: Currency,
) : AndroidViewModel(app) {
    companion object {
        /** Builds the [TimelineViewModel] for [base] → [target]. */
        fun factory(
            app: Application,
            base: Currency,
            target: Currency,
        ): ViewModelProvider.Factory = viewModelFactory { initializer { TimelineViewModel(app, base, target) } }
    }

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

    private var decimalPlaces = DEFAULT_DECIMAL_PLACES

    private val periodLiveData = MutableLiveData(Period.YEAR)
    private val customRangeLiveData = MutableLiveData<Span?>(null)

    // How far back this screen has asked the repository for — a longer span
    // fetches further back; a shorter one filters what's already there.
    private var fetchedSince: LocalDate = defaultTimelineSince()

    // currently selected date
    private val scrubDateLiveData = MutableLiveData<LocalDate?>()

    // error
    private val errorLiveData = repository.getError()

    // updating — see RefreshState: the chart's progress bar is a passive
    // indicator; menu enablement is logic, so it reads the raw state.
    private val refreshIndicator: LiveData<Boolean> = RefreshState.passiveIndicator.asLiveData()
    private val refreshInFlight: LiveData<Boolean> = RefreshState.inFlight.asLiveData()

    private val dbLiveItems: LiveData<Timeline?> by lazy {
        MediatorLiveData<Timeline?>().apply {
            var timeline: Timeline? = null

            fun update() {
                val span = currentSpan()
                this.value =
                    timeline?.copy(
                        startDate = span.start,
                        rates = timeline?.rates?.filterKeys { !it.isBefore(span.start) && !it.isAfter(span.end) },
                    )
            }

            addSource(repository.getTimeline(base, target, fetchedSince)) {
                timeline = it
                update()
            }
            addSource(periodLiveData) { update() }
            addSource(customRangeLiveData) { update() }
        }
    }

    /** The dates on screen: the chosen period's, or the custom range. */
    private fun currentSpan(today: LocalDate = LocalDate.now()): Span {
        val period = periodLiveData.value ?: Period.YEAR
        return period.startDate(today)?.let { Span(it, today) }
            ?: customRangeLiveData.value
            ?: Span(today.minusYears(1), today)
    }

    // Asks for older history when the span on screen starts before what's
    // been fetched; a span within it needs no network.
    private fun fetchCovering(span: Span) {
        if (!span.start.isBefore(fetchedSince)) return
        fetchedSince = span.start
        repository.getTimeline(base, target, fetchedSince)
    }

    /*
     * getters for the various values ==============================================================
     */

    /**
     * The pair on screen, for the title. Follows [toggleCurrencies]; null
     * until the first timeline arrives, so the title doesn't flash the pair
     * over an empty chart.
     */
    fun getCurrencyPair(): LiveData<Pair<Currency, Currency>?> = dbLiveItems.map { if (it == null) null else base to target }

    /** Fetches the pair again — after an error, or once the provider changed. */
    fun retry() {
        repository.getTimeline(base, target, fetchedSince)
    }

    fun toggleCurrencies() {
        val tmp = base
        base = target
        target = tmp
        // call the api -- timeline live data is auto-updated everywhere where it is used
        repository.getTimeline(base, target, fetchedSince)
    }

    fun getProvider(): LiveData<CharSequence?> =
        dbLiveItems.map {
            it?.provider?.getName(app)
        }

    fun getRates(): LiveData<Map<LocalDate, Rate>?> =
        dbLiveItems.map {
            it?.rates
        }

    /**
     * True when the dates on screen hold no rates once loading is done — a
     * custom range over a weekend, or before the provider's history. While
     * older history is still downloading it stays false (the progress bar
     * says what's happening).
     */
    fun isRangeEmpty(): LiveData<Boolean> =
        MediatorLiveData(false).apply {
            var timeline: Timeline? = null
            var loading = false

            fun update() {
                value = !loading && timeline?.rates?.isEmpty() == true
            }

            addSource(dbLiveItems) {
                timeline = it
                update()
            }
            addSource(refreshInFlight) {
                loading = it
                update()
            }
        }

    fun getRateCurrent(): LiveData<Pair<Map.Entry<LocalDate, Rate?>?, Int>> =
        MediatorLiveData<Pair<Map.Entry<LocalDate, Rate?>?, Int>>().apply {
            var rates: Map.Entry<LocalDate, Rate?>? = null

            fun update() {
                this.value = Pair(rates, decimalPlaces)
            }

            addSource(dbLiveItems) {
                rates = it?.rates?.entries?.lastOrNull()
                update()
            }

            addSource(getDecimalPlaces()) {
                decimalPlaces = it
                update()
            }
        }

    fun getRatePast(): LiveData<Pair<Map.Entry<LocalDate, Rate?>?, Int>> =
        MediatorLiveData<Pair<Map.Entry<LocalDate, Rate?>?, Int>>().apply {
            var date: LocalDate? = null
            var rates: Set<Map.Entry<LocalDate, Rate?>>? = null

            fun update() {
                this.value =
                    if (date != null) {
                        Pair(rates?.find { it.key == date }, decimalPlaces)
                    } else {
                        Pair(rates?.firstOrNull(), decimalPlaces)
                    }
            }

            addSource(dbLiveItems) {
                rates = it?.rates?.entries
                update()
            }

            addSource(scrubDateLiveData) {
                date = it
                update()
            }

            addSource(getDecimalPlaces()) {
                decimalPlaces = it
                update()
            }
        }

    fun getRatesDifferencePercent(): LiveData<BigDecimal?> =
        MediatorLiveData<BigDecimal?>().apply {
            var scrubDate: LocalDate? = null
            var rates: Set<Map.Entry<LocalDate, Rate?>>? = null

            fun update() {
                val past =
                    if (scrubDate != null) {
                        rates?.find { it.key == scrubDate }?.value
                    } else {
                        rates?.firstOrNull()?.value
                    }
                val current = rates?.lastOrNull()?.value

                val ratePast = past?.value
                val rateCurrent = current?.value
                this.value = calculateDifference(ratePast, rateCurrent)
            }

            addSource(dbLiveItems) {
                rates = it?.rates?.entries
                update()
            }

            addSource(scrubDateLiveData) {
                scrubDate = it
                update()
            }
        }

    fun getRatesAverage(): LiveData<Pair<Rate?, Int>> = aggregateLiveData(::averageOf)

    fun getRatesMedian(): LiveData<Pair<Rate?, Int>> = aggregateLiveData(::medianOf)

    // Shared MediatorLiveData scaffolding for range-wide statistics that reduce
    // the visible-range value set to a single [Rate]. The reducer receives a
    // non-empty list; an empty range short-circuits to null before it runs.
    private fun aggregateLiveData(reducer: (List<BigDecimal>) -> BigDecimal): LiveData<Pair<Rate?, Int>> =
        MediatorLiveData<Pair<Rate?, Int>>().apply {
            var scrubDate: LocalDate? = null
            var rates: Set<Map.Entry<LocalDate, Rate?>>? = null

            fun update() {
                val values =
                    rates
                        ?.fromScrub(scrubDate)
                        ?.mapNotNull { entry -> entry.value?.value }
                val aggregate: Rate? =
                    if (values.isNullOrEmpty()) {
                        null
                    } else {
                        Rate(target, reducer(values))
                    }
                this.value = Pair(aggregate, decimalPlaces)
            }

            addSource(dbLiveItems) {
                rates = it?.rates?.entries
                update()
            }

            addSource(scrubDateLiveData) {
                scrubDate = it
                update()
            }

            addSource(getDecimalPlaces()) {
                decimalPlaces = it
                update()
            }
        }

    fun getRatesMin(): LiveData<Triple<Rate?, LocalDate?, Int>> = extremeLiveData(pickMax = false, respectScrub = true)

    fun getRatesMax(): LiveData<Triple<Rate?, LocalDate?, Int>> = extremeLiveData(pickMax = true, respectScrub = true)

    // Range-wide extremes for the on-chart reference lines: intentionally
    // ignore the scrub position so the horizontal min/max lines stay anchored
    // to the visible period's absolute extremes while the finger drags.
    fun getRatesRangeMin(): LiveData<Double?> = rangeExtreme(pickMax = false)

    fun getRatesRangeMax(): LiveData<Double?> = rangeExtreme(pickMax = true)

    private fun rangeExtreme(pickMax: Boolean): LiveData<Double?> =
        extremeLiveData(pickMax = pickMax, respectScrub = false).map { it.first?.value?.toDouble() }

    private fun extremeLiveData(
        pickMax: Boolean,
        respectScrub: Boolean,
    ): LiveData<Triple<Rate?, LocalDate?, Int>> =
        MediatorLiveData<Triple<Rate?, LocalDate?, Int>>().apply {
            var scrubDate: LocalDate? = null
            var rates: Set<Map.Entry<LocalDate, Rate?>>? = null

            fun update() {
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
                this.value = Triple(extreme, date, decimalPlaces)
            }

            addSource(dbLiveItems) {
                rates = it?.rates?.entries
                update()
            }

            if (respectScrub) {
                addSource(scrubDateLiveData) {
                    scrubDate = it
                    update()
                }
            }

            addSource(getDecimalPlaces()) {
                decimalPlaces = it
                update()
            }
        }

    private fun getDecimalPlaces(): LiveData<Int> =
        MediatorLiveData<Int>().apply {
            var min = BigDecimal.ZERO
            var max = BigDecimal.ZERO

            fun update() {
                this.value =
                    min(
                        (min - max).abs().getSignificantDecimalPlaces(SIGNIFICANT_DIGITS),
                        MAX_DECIMAL_PLACES,
                    )
            }

            addSource(dbLiveItems) {
                min = it?.rates?.entries?.minOfOrNull { rate -> rate.value.value } ?: BigDecimal.ZERO
                max = it?.rates?.entries?.maxOfOrNull { rate -> rate.value.value } ?: BigDecimal.ZERO
                update()
            }
        }

    fun getPeriod(): LiveData<Period> = periodLiveData

    /** The dates on screen right now (for the chart's share caption). */
    fun span(): Span = currentSpan()

    fun getCustomRange(): LiveData<Span?> = customRangeLiveData

    /** A preset period; [Period.CUSTOM] goes through [setCustomRange]. */
    fun setTimePeriod(period: Period) {
        if (period == Period.CUSTOM && customRangeLiveData.value == null) return
        periodLiveData.value = period
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
        customRangeLiveData.value = Span(from, to)
        periodLiveData.value = Period.CUSTOM
        fetchCovering(Span(from, to))
    }

    fun setPastDate(date: LocalDate?) {
        scrubDateLiveData.postValue(date)
    }

    /*
     * error =======================================================================================
     */

    fun getError(): LiveData<String?> = errorLiveData

    /** Chart progress bar: only for a refresh slow enough to notice. */
    fun isRefreshing(): LiveData<Boolean> = refreshIndicator

    /** Raw "a refresh is running" — for enabling actions, not for display. */
    fun isRefreshInFlight(): LiveData<Boolean> = refreshInFlight
}
