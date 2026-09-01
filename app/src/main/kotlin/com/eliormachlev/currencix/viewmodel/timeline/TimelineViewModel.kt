package com.eliormachlev.currencix.viewmodel.timeline

import android.app.Application
import android.text.Spanned
import android.text.SpannedString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.repository.ExchangeRatesRepository
import com.eliormachlev.currencix.util.calculateDifference
import com.eliormachlev.currencix.util.fromHtmlLegacy
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
    class Factory(
        private val mApplication: Application,
        private val base: Currency,
        private val target: Currency,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TimelineViewModel(mApplication, base, target) as T
    }

    enum class Period {
        WEEK,
        MONTH,
        YEAR,
        ;

        fun startDate(today: LocalDate = LocalDate.now()): LocalDate =
            when (this) {
                WEEK -> today.minusWeeks(1)
                MONTH -> today.minusMonths(1)
                YEAR -> today.minusYears(1)
            }
    }

    private var repository: ExchangeRatesRepository = ExchangeRatesRepository(app)

    private var decimalPlaces = DEFAULT_DECIMAL_PLACES

    // week/month/year
    private val periodLiveData = MutableLiveData(Period.YEAR)

    // currently selected date
    private val scrubDateLiveData = MutableLiveData<LocalDate?>()

    // error
    private val errorLiveData = repository.getError()

    // updating
    private var isUpdating = repository.isUpdating()

    private val dbLiveItems: LiveData<Timeline?> by lazy {
        MediatorLiveData<Timeline?>().apply {
            var timeline: Timeline? = null
            var startDate: LocalDate? = null

            fun update() {
                this.value =
                    timeline?.copy(
                        startDate = startDate,
                        rates =
                            timeline?.rates?.filter { entries ->
                                !entries.key.isBefore(startDate)
                            },
                    )
            }

            // 1y timeline data - always call api - hard to find a decent caching strategy
            addSource(repository.getTimeline(base, target)) {
                timeline = it
                update()
            }

            // selected time period
            addSource(periodLiveData) {
                startDate = it.startDate()
                update()
            }
        }
    }

    /*
     * getters for the various values ==============================================================
     */

    fun getTitle(): LiveData<Spanned> =
        dbLiveItems.map {
            if (it == null) {
                SpannedString("")
            } else {
                app
                    .getString(
                        R.string.activity_timeline_title,
                        base.iso4217Alpha(),
                        target.iso4217Alpha(),
                    ).fromHtmlLegacy()
            }
        }

    fun toggleCurrencies() {
        val tmp = base
        base = target
        target = tmp
        // call the api -- timeline live data is auto-updated everywhere where it is used
        repository.getTimeline(base, target)
    }

    fun getProvider(): LiveData<CharSequence?> =
        dbLiveItems.map {
            it?.provider?.getName()
        }

    fun getRates(): LiveData<Map<LocalDate, Rate>?> =
        dbLiveItems.map {
            it?.rates
        }

    fun getRateCurrent(): LiveData<Pair<Map.Entry<LocalDate, Rate?>?, Int>> =
        MediatorLiveData<Pair<Map.Entry<LocalDate, Rate?>?, Int>>().apply {
            var rates: Map.Entry<LocalDate, Rate?>? = null

            fun update() {
                this.value = Pair(rates, decimalPlaces)
            }

            addSource(dbLiveItems) {
                rates = it?.rates?.entries?.last()
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
                        Pair(rates?.first(), decimalPlaces)
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
                        rates?.first()?.value
                    }
                val current = rates?.last()?.value

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

    fun setTimePeriod(period: Period) {
        periodLiveData.postValue(period)
    }

    fun setPastDate(date: LocalDate?) {
        scrubDateLiveData.postValue(date)
    }

    /*
     * error =======================================================================================
     */

    fun getError(): LiveData<String?> = errorLiveData

    fun isUpdating(): LiveData<Boolean> = isUpdating
}
