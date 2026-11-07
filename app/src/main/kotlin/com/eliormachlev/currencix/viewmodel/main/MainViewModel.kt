package com.eliormachlev.currencix.viewmodel.main

import android.app.Application
import android.text.SpannableStringBuilder
import androidx.core.text.bold
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeCalculator
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.rateFor
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.ExchangeRatesRepository
import com.eliormachlev.currencix.repository.RefreshState
import com.eliormachlev.currencix.util.evaluateCalculatorExpression
import com.eliormachlev.currencix.util.getDecimalSeparator
import com.eliormachlev.currencix.util.isCurrencySymbolOnRight
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.viewmodel.util.stateInEagerly
import com.eliormachlev.currencix.viewmodel.util.stateInWhileSubscribed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.text.Collator
import java.time.LocalDate
import java.time.ZoneId

class MainViewModel(
    private val app: Application,
    onlyCache: Boolean = false,
) : AndroidViewModel(app) {
    companion object {
        /** Builds a [MainViewModel]; with [onlyCache] it never asks the network (the currency picker's copy). */
        fun factory(
            app: Application,
            onlyCache: Boolean = false,
        ): ViewModelProvider.Factory = viewModelFactory { initializer { MainViewModel(app, onlyCache) } }
    }

    private val repository: ExchangeRatesRepository = ExchangeRatesRepository(app)
    private val db = Database(app)

    private val starredLiveItems: StateFlow<ImmutableList<Currency>> =
        db.stars.starredCurrencies.flow
            .stateInWhileSubscribed(viewModelScope, db.stars.starredCurrencies.get())
    private val onlyShowStarred: StateFlow<Boolean> =
        db.stars.filterStarred.flow
            .stateInWhileSubscribed(viewModelScope, db.stars.filterStarred.get())

    // ui
    // Refresh indicators — debounced for display; see RefreshState.
    private val refreshSpinner: StateFlow<Boolean> =
        RefreshState.pullIndicator.stateInWhileSubscribed(viewModelScope, false)
    private val refreshShimmer: StateFlow<Boolean> =
        RefreshState.passiveIndicator.stateInWhileSubscribed(viewModelScope, false)
    val isExpandedKeypadEnabled: StateFlow<Boolean> =
        db.display.expandedKeypad.stateInWhileSubscribed(viewModelScope)
    private val decimalPlaces: StateFlow<Int> =
        db.display.decimalPlaces.stateInWhileSubscribed(viewModelScope)

    /** What the keypad has typed. The keypad drives it directly; the amounts below follow it. */
    internal val input = CalculatorInputState()

    private val fees: StateFlow<ImmutableList<Fee>> =
        db.fees.fees.flow
            .stateInWhileSubscribed(viewModelScope, db.fees.fees.get())
    private val activeExchangeId: StateFlow<String?> =
        db.fees.activeExchangeId.flow
            .stateInWhileSubscribed(viewModelScope, db.fees.activeExchangeId.get())
    private val activeBankId: StateFlow<String?> =
        db.fees.activeBankId.flow
            .stateInWhileSubscribed(viewModelScope, db.fees.activeBankId.get())

    /*
     * repository data =============================================================================
     */

    // The rates, sorted by the currencies' names. Null until the first set
    // arrives; after that the last set stays, even if the store is emptied.
    // Collected for the ViewModel's whole life, like everything the selected
    // pair and the conversion are built from: the timeline prefetch below
    // follows the pair, and the share text and the picker read these values
    // synchronously.
    private val exchangeRates: StateFlow<ExchangeRates?> =
        ratesSource(onlyCache)
            .filterNotNull()
            .map(::sortedByName)
            .stateInEagerly(viewModelScope, null)

    // currency selection
    // update currently selected currencies when rates are updated:
    // sometimes the selected rates aren't available anymore, so reset them
    private val currentBaseCurrency: StateFlow<Currency?> =
        combine(db.lastState.lastBaseCurrency.flow, exchangeRates) { base, rates ->
            // last used is present in the current currency set
            rates?.rateFor(base)?.currency
                // not present, so just return the first of the set
                ?: rates?.rates?.firstOrNull()?.currency
        }.stateInEagerly(viewModelScope, null)

    // Cross-observes the resolved base so a stale-prefs collision (both sides
    // pointing at the same currency on first read after an
    // install/migration/import) is corrected to a distinct fallback on the
    // destination side. The grey-out picker prevents users from ever creating
    // this state; this is the belt-and-braces safety net for state loaded
    // from disk. Nothing until the rates arrive.
    private val currentDestinationCurrency: StateFlow<Currency?> =
        combineTransform(db.lastState.lastDestinationCurrency.flow, exchangeRates, currentBaseCurrency) { destination, rates, base ->
            val available = rates?.rates ?: return@combineTransform
            emit(destinationFrom(available, destination, base))
        }.stateInEagerly(viewModelScope, null)

    init {
        // Background timeline prefetcher: fires whenever the selected
        // base/target resolves (including cold-start defaults) so the graph
        // screen paints instantly when the user opens it — otherwise the very
        // first graph tap still pays the full network fetch. The repository
        // dedupes per-pair, so redundant calls collapse into one fetch.
        combine(currentBaseCurrency, currentDestinationCurrency) { base, destination -> prefetchTimeline(base, destination) }
            .launchIn(viewModelScope)
    }

    // The rates to show: the cached set, after asking the network for a new
    // one when the cache is out of date.
    private fun ratesSource(onlyCache: Boolean): Flow<ExchangeRates?> {
        // only update if data is old: https://github.com/Formicka/exchangerate.host
        // "Rates are updated around midnight UTC every working day."
        val currentDate = LocalDate.now(ZoneId.of("UTC"))
        val cachedDate = db.rates.getDate()
        val historicalDate = db.lastState.historicalDate.get()
        val useCache =
            when {
                // force-use cache
                onlyCache -> true

                // first run: fetch data
                cachedDate == null -> false

                // Historical rates in use: serve from cache when the cached date
                // already matches the requested historical date; otherwise re-fetch.
                historicalDate != null -> historicalDate == cachedDate

                // fetch if stored date is before the current date, else just use the cached value
                else -> !cachedDate.isBefore(currentDate)
            }
        return if (useCache) db.rates.getExchangeRates() else repository.getExchangeRates()
    }

    // A copy of [rates] with the rates in the order shown: by the currencies'
    // full names (locale-aware).
    private fun sortedByName(rates: ExchangeRates): ExchangeRates =
        rates.copy(rates = rates.rates?.sortedWith(compareBy(Collator.getInstance()) { rate -> rate.currency.fullName(app) }))

    // The [destination] last used if [available] has it, else the first
    // there; never the same as [base] while another is there.
    private fun destinationFrom(
        available: List<Rate>,
        destination: Currency?,
        base: Currency?,
    ): Currency? {
        val stored = available.find { it.currency == destination }?.currency
        val fallback = available.firstOrNull()?.currency
        val picked = stored ?: fallback
        return if (picked != null && picked == base) {
            available.firstOrNull { it.currency != base }?.currency ?: picked
        } else {
            picked
        }
    }

    /**
     * all the current rates and/or an error message, if present
     */
    internal fun getExchangeRates(): StateFlow<ExchangeRates?> = exchangeRates

    /**
     * Update the data, past the cache's freshness window: the server is asked
     * whether the cached answer still holds (a few bytes if it does).
     */
    internal fun forceUpdateExchangeRate() {
        // The rates already follow the store the refresh writes into.
        if (!RefreshState.inFlight.value) repository.refreshExchangeRates(fresh = true)
    }

    /**
     * [forceUpdateExchangeRate] on getting back online — once any refresh
     * still running has ended, since that one started without a connection
     * and its outcome says nothing about now.
     */
    internal fun refreshAfterReconnect() {
        viewModelScope.launch {
            RefreshState.inFlight.first { running -> !running }
            forceUpdateExchangeRate()
        }
    }

    /**
     * all the currencies that the user has starred
     */
    internal fun getStarredCurrencies(): StateFlow<ImmutableList<Currency>> = starredLiveItems

    /**
     * persist the user's manual ordering of starred currencies
     */
    internal fun setStarredCurrencyOrder(currencies: List<Currency>) {
        db.stars.starredCurrencies.set(currencies.toImmutableList())
    }

    /**
     * whether the currencies should be filtered
     */
    internal fun isFilterStarredEnabled(): StateFlow<Boolean> = onlyShowStarred

    /**
     * switch the starred-filter on/off
     */
    internal fun toggleStarredActive() {
        db.stars.toggleStarredActive()
    }

    /**
     * de-/star a currency
     */
    internal fun toggleCurrencyStar(currencyCode: Currency) {
        db.stars.toggleCurrencyStar(currencyCode)
    }

    /**
     * Each refresh's outcome: null when it succeeded, else the error message
     * (the latest is replayed to a new collector). A [SharedFlow], so a
     * failure repeating the last one's message still arrives.
     */
    internal fun getError(): SharedFlow<String?> = repository.getError()

    /**
     * Pull-to-refresh spinner state: up as soon as a rate refresh starts,
     * held briefly so an instant refresh doesn't blink.
     */
    internal fun isRefreshing(): StateFlow<Boolean> = refreshSpinner

    /**
     * Passive "rates are updating" shimmer: only for a refresh slow enough
     * to notice, so cached refreshes show nothing.
     */
    internal fun isRefreshShimmerVisible(): StateFlow<Boolean> = refreshShimmer

    /**
     * all configured fees
     */
    internal fun getFees(): StateFlow<ImmutableList<Fee>> = fees

    /*
     * base and destination text ===================================================================
     */

    /**
     * the total base value
     */
    private val currentBaseValue: StateFlow<String?> =
        combine(input.baseValueText, input.calculationValueText) { baseValueText, calculationValueText ->
            if (isInCalculationMode()) {
                calculationValueText?.evaluateCalculatorExpression()
            } else {
                baseValueText
            }
        }.stateInEagerly(viewModelScope, null)

    // Read synchronously (the share text, the currency picker), so kept
    // current; null until the base value is known.
    private val currentBaseValueAsNumber: StateFlow<BigDecimal?> =
        currentBaseValue.map(::toAmount).stateInEagerly(viewModelScope, null)

    /**
     * the total base value, as BigDecimal (internal is string)
     */
    internal fun getCurrentBaseValueAsNumber(): StateFlow<BigDecimal?> = currentBaseValueAsNumber

    private val currentBaseValueFormattedState: StateFlow<SpannableStringBuilder?> by lazy {
        combine(currentBaseValue, currentBaseCurrency) { value, currency ->
            val number = (
                value?.toHumanReadableNumber(
                    app,
                    trim = isInCalculationMode(),
                    decimalPlaces = if (isInCalculationMode()) 2 else null,
                ) ?: "0"
            )
            buildBoldNumberWithSymbol(number, currency?.symbol())
        }.stateInWhileSubscribed(viewModelScope, null)
    }

    /**
     * the nicely formatted, total base value including the currency symbol at the right position.
     */
    internal fun getCurrentBaseValueFormatted(): StateFlow<SpannableStringBuilder?> = currentBaseValueFormattedState

    // Bold [number] plus the currency [symbol] on the side dictated by the
    // active locale. Extracted so the base-value and result displays can't
    // disagree on which side the symbol lands.
    private fun buildBoldNumberWithSymbol(
        number: String,
        symbol: String?,
    ): SpannableStringBuilder =
        if (isCurrencySymbolOnRight(app)) {
            SpannableStringBuilder() // 123 $
                .bold { append(number) }
                .append(if (symbol != null) " $symbol" else "")
        } else {
            SpannableStringBuilder() // $ 123
                .append(if (symbol != null) "$symbol " else "")
                .bold { append(number) }
        }

    // ===============================

    private val calculationInputFormattedState: StateFlow<String?> by lazy {
        input.calculationValueText
            .map { it?.replace(".", getDecimalSeparator(app)) }
            .stateInWhileSubscribed(viewModelScope, null)
    }

    /**
     * the nicely formatted calculation string: e.g. 4 + 2.2 - 4 / 2
     */
    internal fun getCalculationInputFormatted(): StateFlow<String?> = calculationInputFormattedState

    // ===============================

    // Fan-in helper: combines fees + current pair + single-select picks into
    // one derived value. Both feeStack and activeFees use it since they only
    // differ in the final calculator call.
    private fun <T> pairFeeFlow(compute: (List<Fee>, Currency?, Currency?, String?, String?) -> T): Flow<T> =
        combine(
            fees,
            currentBaseCurrency,
            currentDestinationCurrency,
            activeExchangeId,
            activeBankId,
        ) { list, base, dest, exchangeId, bankId -> compute(list, base, dest, exchangeId, bankId) }

    /**
     * Multiplicative fee stack for the current pair. Exposed so the UI can
     * render inline fee annotations and derive the "true cost" companion row.
     * Kept current: the fee-adjusted result is built on it.
     */
    private val feeStack: StateFlow<BigDecimal?> =
        pairFeeFlow { list, base, dest, exchangeId, bankId ->
            FeeCalculator.feeStack(list, base, dest, exchangeId, bankId)
        }.stateInEagerly(viewModelScope, null)

    /**
     * The active fees participating in [feeStack] for the current pair —
     * specific-pair matches plus the currently-picked single global
     * exchange / bank-or-card entries. Exposed as [ImmutableList] so Compose
     * stability inference can skip recomposition of consumers when the
     * derived list has equal content across emissions (#161). Kept current:
     * the share text reads it.
     */
    private val activeFees: StateFlow<ImmutableList<Fee>?> =
        pairFeeFlow { list, base, dest, exchangeId, bankId ->
            FeeCalculator.activeFees(list, base, dest, exchangeId, bankId).toImmutableList()
        }.stateInEagerly(viewModelScope, null)

    /**
     * the total destination value — fees don't touch the displayed result;
     * they surface as "true cost" on the input side instead. Null until both
     * currencies have a rate; after that the last result stays while one is
     * missing.
     */
    private val result: StateFlow<String?> =
        combineTransform(
            exchangeRates,
            currentBaseValue,
            currentBaseCurrency,
            currentDestinationCurrency,
        ) { rates, baseValue, base, destination ->
            val amount: BigDecimal = baseValue?.toBigDecimal() ?: BigDecimal.ZERO
            val known = rates ?: return@combineTransform
            val baseRate = known.rateFor(base) ?: return@combineTransform
            val destinationRate = known.rateFor(destination) ?: return@combineTransform
            val fair =
                amount
                    .divide(baseRate.value, MathContext.DECIMAL128)
                    .multiply(destinationRate.value)
            emit(fair.toPlainString())
        }.stateInEagerly(viewModelScope, null)

    /**
     * Fee stack for an arbitrary pair — used by ad-hoc UIs
     * (e.g. the quick-conversions popup) that need to apply fees outside
     * the main result pipeline.
     */
    internal fun feeStackFor(
        base: Currency?,
        dest: Currency?,
    ): BigDecimal =
        FeeCalculator.feeStack(
            fees.value,
            base,
            dest,
            activeExchangeId.value,
            activeBankId.value,
        )

    /**
     * Multiplicative fee stack for the current pair.
     */
    internal fun getFeeStack(): StateFlow<BigDecimal?> = feeStack

    /**
     * Active fees participating for the current pair.
     */
    internal fun getActiveFees(): StateFlow<ImmutableList<Fee>?> = activeFees

    // Null (no result yet) stays null; read synchronously (the share text,
    // the currency picker), so kept current.
    private val resultAsNumber: StateFlow<BigDecimal?> =
        result.map { it?.let(::toAmount) }.stateInEagerly(viewModelScope, null)

    /**
     * the total destination value, as BigDecimal (internal is string)
     */
    internal fun getResultAsNumber(): StateFlow<BigDecimal?> = resultAsNumber

    // `result * feeStack` — the fee-adjusted destination value. When no fee
    // applies, equals `result`. Used by the True Cost panel so users see the
    // final out-of-pocket cost expressed in the destination currency
    // (e.g. paying `$200` after fees ≈ `604.3 ILS`, not the fee-free `302.3`).
    private val resultWithFees: StateFlow<String?> =
        combine(result, feeStack) { r, s ->
            val amount = r?.toBigDecimalOrNull() ?: return@combine null
            val stack = s ?: BigDecimal.ONE
            amount.multiply(stack, MathContext.DECIMAL128).toPlainString()
        }.stateInEagerly(viewModelScope, null)

    // Null only until the pipeline has run once; zero while there's no
    // result. Kept current: the share text reads it.
    private val resultWithFeesAsNumber: StateFlow<BigDecimal?> =
        resultWithFees.map(::toAmount).stateInEagerly(viewModelScope, null)

    /**
     * the fee-adjusted destination value, as BigDecimal — result × feeStack.
     */
    internal fun getResultWithFeesAsNumber(): StateFlow<BigDecimal?> = resultWithFeesAsNumber

    private val resultFormattedState: StateFlow<SpannableStringBuilder?> by lazy { formattedDestinationAmount(result) }

    /**
     * the nicely formatted, total destination value including the currency symbol at the right position.
     */
    internal fun getResultFormatted(): StateFlow<SpannableStringBuilder?> = resultFormattedState

    private val resultWithFeesFormattedState: StateFlow<SpannableStringBuilder?> by lazy {
        formattedDestinationAmount(resultWithFees)
    }

    /**
     * the nicely formatted, fee-adjusted destination value (True Cost).
     */
    internal fun getResultWithFeesFormatted(): StateFlow<SpannableStringBuilder?> = resultWithFeesFormattedState

    // Formats a destination-currency numeric string ("302.3") into the hero's
    // bold-number-plus-currency-symbol SpannableStringBuilder, tracking the
    // active destination currency and decimal-places preference. Shared by
    // the fair-conversion and true-cost pipelines so they format identically.
    private fun formattedDestinationAmount(source: Flow<String?>): StateFlow<SpannableStringBuilder?> =
        combine(source, currentDestinationCurrency, decimalPlaces) { resultText, currency, places ->
            val number = (
                resultText?.toHumanReadableNumber(
                    app,
                    trim = true,
                    decimalPlaces = places,
                ) ?: "0"
            )
            buildBoldNumberWithSymbol(number, currency?.symbol())
        }.stateInWhileSubscribed(viewModelScope, null)

    /**
     * the current decimal-places preference, for output-side rounding.
     */
    internal fun getDecimalPlaces(): StateFlow<Int> = decimalPlaces

    /*
     * selected currencies *************************************************************************
     */

    internal fun setBaseCurrency(currency: Currency) {
        db.lastState.saveLastUsedRates(
            currency,
            currentDestinationCurrency.value,
        )
        prefetchTimeline(currency, currentDestinationCurrency.value)
    }

    internal fun setDestinationCurrency(currency: Currency) {
        db.lastState.saveLastUsedRates(
            currentBaseCurrency.value,
            currency,
        )
        prefetchTimeline(currentBaseCurrency.value, currency)
    }

    /** Sets both sides in one write, so neither side reads the other mid-change. */
    internal fun setCurrencyPair(pair: CurrencyPair) {
        if (pair.from == pair.to) return
        db.lastState.saveLastUsedRates(pair.from, pair.to)
        prefetchTimeline(pair.from, pair.to)
    }

    /** Flips base and destination (the swap button); no-op until both are known. */
    internal fun swapCurrencies() {
        val from = currentBaseCurrency.value ?: return
        val to = currentDestinationCurrency.value ?: return
        setCurrencyPair(CurrencyPair(from = to, to = from))
    }

    // Warm the timeline for the currently-selected pair in the background so
    // the graph screen paints instantly when the user opens it. The repository
    // dedupes in-flight fetches per pair and merges into its per-pair cache,
    // so repeated calls are cheap.
    private fun prefetchTimeline(
        base: Currency?,
        target: Currency?,
    ) {
        if (base == null || target == null) return
        repository.getTimeline(base, target)
    }

    internal fun getBaseCurrency(): StateFlow<Currency?> = currentBaseCurrency

    internal fun getDestinationCurrency(): StateFlow<Currency?> = currentDestinationCurrency

    /*
     * historical rates
     */

    internal fun setHistoricalDate(date: LocalDate?) {
        // check if previous date was "latest" or historical
        val wasLatestActive = db.lastState.historicalDate.get() == null
        // save selected historical date to db
        db.lastState.historicalDate.set(date)
        // refresh, if new date != cached date or if last state was "latest"
        if (date != db.rates.getDate() || wasLatestActive) {
            forceUpdateExchangeRate()
        }
    }

    internal fun getHistoricalDate(): LocalDate? = db.lastState.historicalDate.get()

    internal fun getHistoricalLiveDate(): Flow<LocalDate?> = db.lastState.historicalDate.flow

    /*
     * helpers =====================================================================================
     */

    private fun isInCalculationMode(): Boolean = input.isInCalculationMode()
}

// An amount the pipeline holds as text, as a number: zero when there is none.
private fun toAmount(text: String?): BigDecimal = text?.toBigDecimalOrNull() ?: BigDecimal.ZERO
