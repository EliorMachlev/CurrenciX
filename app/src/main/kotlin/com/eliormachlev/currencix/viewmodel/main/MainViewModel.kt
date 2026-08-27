package com.eliormachlev.currencix.viewmodel.main

import android.app.Application
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.core.text.bold
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeCalculator
import com.eliormachlev.currencix.model.FeeSide
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.ExchangeRatesRepository
import com.eliormachlev.currencix.util.OPERATOR_DIVIDE
import com.eliormachlev.currencix.util.OPERATOR_MINUS
import com.eliormachlev.currencix.util.OPERATOR_MULTIPLY
import com.eliormachlev.currencix.util.OPERATOR_PLUS
import com.eliormachlev.currencix.util.combineWith
import com.eliormachlev.currencix.util.evaluateCalculatorExpression
import com.eliormachlev.currencix.util.fromHtmlLegacy
import com.eliormachlev.currencix.util.getDecimalSeparator
import com.eliormachlev.currencix.util.getSignificantDecimalPlaces
import com.eliormachlev.currencix.util.hasAppendedCurrencySymbol
import com.eliormachlev.currencix.util.normaliseGlyphsToAscii
import com.eliormachlev.currencix.util.toHumanReadableNumber
import java.math.BigDecimal
import java.math.MathContext
import java.text.Collator
import java.time.LocalDate
import java.time.ZoneId

@Suppress("unused", "MemberVisibilityCanBePrivate")
class MainViewModel(
    val app: Application,
    onlyCache: Boolean = false,
) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, false)

    class Factory(
        val app: Application,
        val onlyCache: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app, onlyCache) as T
    }

    private var repository: ExchangeRatesRepository = ExchangeRatesRepository(app)
    private val db = Database(app)

    // repository data
    private var dbLiveItems: LiveData<ExchangeRates?>
    private var exchangeRates: LiveData<ExchangeRates?>
    private val starredLiveItems: LiveData<List<Currency>>
    private val onlyShowStarred: LiveData<Boolean>
    private val liveError = repository.getError()

    // ui
    private var isUpdating: LiveData<Boolean> = repository.isUpdating()
    val keyboardType: LiveData<KeyboardType> = db.getKeyboardType()
    val isExtendedKeypadEnabled: LiveData<Boolean> = keyboardType.map { it == KeyboardType.EXPANDED }
    val isHapticFeedbackEnabled: LiveData<Boolean> = db.isHapticFeedbackEnabled()
    private val decimalPlaces: LiveData<Int> = db.getDecimalPlaces()

    // number input
    private val input = CalculatorInputState()
    private val currentBaseValueText: LiveData<String?> = input.baseValueText
    private val currentCalculationValueText: LiveData<String?> = input.calculationValueText

    // currency selection
    private val currentBaseCurrency: LiveData<Currency?>
    private val currentDestinationCurrency: LiveData<Currency?>

    // fees
    private val fees: LiveData<List<Fee>>
    private val feeSide: LiveData<FeeSide>
    private val activeExchangeId: LiveData<String?>
    private val activeBankId: LiveData<String?>

    // Background timeline prefetcher: fires whenever the selected base/target
    // resolves (including cold-start defaults) so the graph screen paints
    // instantly when the user opens it. MediatorLiveData needs at least one
    // active observer to receive updates from its sources; the value itself is
    // never read, so a no-op keep-alive observer is enough.
    private val timelinePrefetch = MediatorLiveData<Unit>()
    private val timelinePrefetchKeepAlive = Observer<Unit> { /* no-op */ }

    /*
     * repository data =============================================================================
     */

    init {
        // only update if data is old: https://github.com/Formicka/exchangerate.host
        // "Rates are updated around midnight UTC every working day."
        val currentDate = LocalDate.now(ZoneId.of("UTC"))
        val cachedDate = db.getDate()
        val historicalDate = db.getHistoricalDate()

        dbLiveItems =
            when {
                // force-use cache
                onlyCache -> db.getExchangeRates()
                // first run: fetch data
                cachedDate == null -> repository.getExchangeRates()
                // Historical rates in use: serve from cache when the cached date
                // already matches the requested historical date; otherwise re-fetch.
                historicalDate != null -> {
                    if (historicalDate == cachedDate) {
                        db.getExchangeRates()
                    } else {
                        repository.getExchangeRates()
                    }
                }
                // fetch if stored date is before the current date
                cachedDate.isBefore(currentDate) -> repository.getExchangeRates()
                // else just use the cached value
                else -> db.getExchangeRates()
            }

        starredLiveItems = db.getStarredCurrencies()
        onlyShowStarred = db.isFilterStarredEnabled()

        fees = db.getFees()
        feeSide = db.getFeeSide()
        activeExchangeId = db.getActiveExchangeId()
        activeBankId = db.getActiveBankId()

        //
        exchangeRates =
            object : MediatorLiveData<ExchangeRates?>() {
                var liveItems: ExchangeRates? = null

                init {
                    addSource(dbLiveItems) {
                        liveItems = it
                        calc()
                    }
                    addSource(starredLiveItems) { calc() }
                    addSource(onlyShowStarred) { calc() }
                }

                private fun calc() {
                    liveItems?.let { rates ->
                        this.value =
                            rates
                                // usa a copy with ...
                                .copy(
                                    rates =
                                        rates.rates
                                            // ... the correct sort order of the rates
                                            ?.sortedWith(
                                                // sort by full name (locale-aware)
                                                compareBy(Collator.getInstance()) { rate ->
                                                    rate.currency.fullName(app)
                                                },
                                            ),
                                )
                    }
                }
            }

        // update currently selected currencies when rates are updated:
        // sometimes the selected rates aren't available anymore, so reset them
        val baseCurrency = db.getLastBaseCurrency()
        val destinationCurrency = db.getLastDestinationCurrency()
        currentBaseCurrency =
            object : MediatorLiveData<Currency?>() {
                var base: Currency? = null
                var rates: ExchangeRates? = null

                init {
                    addSource(baseCurrency) {
                        base = it
                        update()
                    }
                    addSource(exchangeRates) {
                        rates = it
                        update()
                    }
                }

                private fun update() {
                    this.value =
                        // last used is present in the current currency set
                        rates?.rates?.find { it.currency == base }?.currency
                            // not present, so just return the first of the set
                            ?: rates?.rates?.firstOrNull()?.currency
                }
            }
        currentDestinationCurrency =
            object : MediatorLiveData<Currency?>() {
                var destination: Currency? = null
                var rates: ExchangeRates? = null
                var base: Currency? = null

                init {
                    addSource(destinationCurrency) {
                        destination = it
                        update()
                    }
                    addSource(exchangeRates) {
                        rates = it
                        update()
                    }
                    // Cross-observe the resolved base so a stale-prefs
                    // collision (both sides pointing at the same currency on
                    // first read after an install/migration/import) is
                    // corrected to a distinct fallback on the destination
                    // side. The grey-out picker prevents users from ever
                    // creating this state; this is the belt-and-braces
                    // safety net for state loaded from disk.
                    addSource(currentBaseCurrency) {
                        base = it
                        update()
                    }
                }

                private fun update() {
                    val available = rates?.rates ?: return
                    val stored = available.find { it.currency == destination }?.currency
                    val fallback = available.firstOrNull()?.currency
                    val picked = stored ?: fallback
                    this.value =
                        if (picked != null && picked == base) {
                            available.firstOrNull { it.currency != base }?.currency ?: picked
                        } else {
                            picked
                        }
                }
            }

        // Also prefetch on cold start, once both base/target resolve from prefs
        // or defaults — otherwise the very first graph tap still pays the full
        // network fetch. The repository dedupes per-pair, so the redundant
        // callbacks (base fires, then target fires) collapse into one fetch.
        timelinePrefetch.addSource(currentBaseCurrency) {
            prefetchTimeline(it, currentDestinationCurrency.value)
        }
        timelinePrefetch.addSource(currentDestinationCurrency) {
            prefetchTimeline(currentBaseCurrency.value, it)
        }
        timelinePrefetch.observeForever(timelinePrefetchKeepAlive)
    }

    override fun onCleared() {
        timelinePrefetch.removeObserver(timelinePrefetchKeepAlive)
        super.onCleared()
    }

    /**
     * all the current rates and/or an error message, if present
     */
    internal fun getExchangeRates(): LiveData<ExchangeRates?> = exchangeRates

    /**
     * update the data, without checking the cache
     */
    internal fun forceUpdateExchangeRate() {
        if (isUpdating.value != true) {
            dbLiveItems = repository.getExchangeRates()
        }
    }

    /**
     * all the currencies that the user has starred
     */
    internal fun getStarredCurrencies(): LiveData<List<Currency>> = starredLiveItems

    /**
     * persist the user's manual ordering of starred currencies
     */
    internal fun setStarredCurrencyOrder(currencies: List<Currency>) {
        db.setStarredCurrencyOrder(currencies)
    }

    /**
     * whether the currencies should be filtered
     */
    internal fun isFilterStarredEnabled(): LiveData<Boolean> = onlyShowStarred

    /**
     * switch the starred-filter on/off
     */
    internal fun toggleStarredActive() {
        db.toggleStarredActive()
    }

    /**
     * de-/star a currency
     */
    internal fun toggleCurrencyStar(currencyCode: Currency) {
        db.toggleCurrencyStar(currencyCode)
    }

    /**
     * the error message, if present
     */
    internal fun getError(): LiveData<String?> = liveError

    /**
     * if the app is updating the rates
     */
    internal fun isUpdating(): LiveData<Boolean> = isUpdating

    /**
     * all configured fees
     */
    internal fun getFees(): LiveData<List<Fee>> = fees

    /**
     * on which side of the conversion the fee should be applied
     */
    internal fun getFeeSide(): LiveData<FeeSide> = feeSide

    /**
     * persist the fee side.
     */
    internal fun setFeeSide(side: FeeSide) {
        db.setFeeSide(side)
    }

    internal val ratesInformationFooter =
        object : MediatorLiveData<Spanned?>() {
            var exchangeRates: ExchangeRates? = null
            var baseCurrency: Currency? = null
            var destinationCurrency: Currency? = null

            init {
                addSource(getExchangeRates()) {
                    exchangeRates = it
                    update()
                }
                addSource(currentBaseCurrency) {
                    baseCurrency = it
                    update()
                }
                addSource(currentDestinationCurrency) {
                    destinationCurrency = it
                    update()
                }
            }

            fun update() {
                if (exchangeRates != null && baseCurrency != null && destinationCurrency != null) {
                    // base currency
                    val baseValue = exchangeRates!!.rates?.find { it.currency == baseCurrency }?.value
                    // target currency
                    val destinationValue = exchangeRates!!.rates?.find { it.currency == destinationCurrency }?.value
                    val destinationValueCalculated =
                        baseValue?.let {
                            destinationValue?.divide(it, MathContext.DECIMAL128)
                        }

                    // create string
                    this.value =
                        app
                            .getString(
                                R.string.info_conversion,
                                "1",
                                baseCurrency!!.iso4217Alpha(),
                                destinationValueCalculated?.toHumanReadableNumber(
                                    app,
                                    decimalPlaces = destinationValueCalculated.getSignificantDecimalPlaces(2),
                                ) ?: "",
                                destinationCurrency!!.iso4217Alpha(),
                            ).fromHtmlLegacy()
                }
            }
        }

    /*
     * base and destination text ===================================================================
     */

    /**
     * the total base value
     */
    private val currentBaseValue =
        object : MediatorLiveData<String?>() {
            var baseValueText: String? = null
            var calculationValueText: String? = null

            init {
                addSource(currentBaseValueText) {
                    baseValueText = it
                    update()
                }
                addSource(currentCalculationValueText) {
                    calculationValueText = it
                    update()
                }
            }

            fun update() {
                this.value =
                    if (isInCalculationMode()) {
                        calculationValueText?.evaluateCalculatorExpression()
                    } else {
                        baseValueText
                    }
            }
        }

    /**
     * the total base value, as BigDecimal (internal is string)
     */
    internal fun getCurrentBaseValueAsNumber(): LiveData<BigDecimal> =
        currentBaseValue.map {
            it?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        }

    /**
     * the nicely formatted, total base value including the currency symbol at the right position.
     */
    internal fun getCurrentBaseValueFormatted(): LiveData<SpannableStringBuilder> =
        currentBaseValue.combineWith(currentBaseCurrency) { value, currency ->
            val number = (
                value?.toHumanReadableNumber(
                    app,
                    trim = isInCalculationMode(),
                    decimalPlaces = if (isInCalculationMode()) 2 else null,
                ) ?: "0"
            )
            buildBoldNumberWithSymbol(number, currency?.symbol())
        }

    // Bold [number] plus the currency [symbol] on the side dictated by the
    // active locale. Extracted so the base-value and result displays can't
    // disagree on which side the symbol lands.
    private fun buildBoldNumberWithSymbol(
        number: String,
        symbol: String?,
    ): SpannableStringBuilder =
        if (hasAppendedCurrencySymbol(app)) {
            SpannableStringBuilder() // 123 $
                .bold { append(number) }
                .append(if (symbol != null) " $symbol" else "")
        } else {
            SpannableStringBuilder() // $ 123
                .append(if (symbol != null) "$symbol " else "")
                .bold { append(number) }
        }

    // ===============================

    /**
     * the nicely formatted calculation string: e.g. 4 + 2.2 - 4 / 2
     */
    internal fun getCalculationInputFormatted(): LiveData<String?> =
        currentCalculationValueText.map {
            it?.replace(".", getDecimalSeparator(app))
        }

    // ===============================

    /**
     * the total destination value
     */
    private val result =
        object : MediatorLiveData<String>() {
            var rates: ExchangeRates? = null
            var baseValue: String? = null
            var baseCurrency: Currency? = null
            var destinationCurrency: Currency? = null
            var feeList: List<Fee>? = null
            var side: FeeSide? = null
            var exchangeId: String? = null
            var bankId: String? = null

            init {
                // rates changed
                addSource(exchangeRates) {
                    rates = it
                    calculateResult()
                }
                // base input changed
                addSource(currentBaseValue) {
                    baseValue = it
                    calculateResult()
                }
                // base currency changed
                addSource(currentBaseCurrency) {
                    baseCurrency = it
                    calculateResult()
                }
                // destination currency changed
                addSource(currentDestinationCurrency) {
                    destinationCurrency = it
                    calculateResult()
                }
                // fees changed
                addSource(fees) {
                    feeList = it
                    calculateResult()
                }
                // fee side toggled
                addSource(feeSide) {
                    side = it
                    calculateResult()
                }
                // active picker changed
                addSource(activeExchangeId) {
                    exchangeId = it
                    calculateResult()
                }
                addSource(activeBankId) {
                    bankId = it
                    calculateResult()
                }
            }

            private fun calculateResult() {
                val amount: BigDecimal = baseValue?.toBigDecimal() ?: BigDecimal.ZERO
                val baseRate = rates?.rates?.find { it.currency == baseCurrency }
                val destinationRate = rates?.rates?.find { it.currency == destinationCurrency }

                if (baseRate != null && destinationRate != null) {
                    val fair =
                        amount
                            .divide(baseRate.value, MathContext.DECIMAL128)
                            .multiply(destinationRate.value)
                    val displayed =
                        when (side ?: FeeSide.ORIGINAL) {
                            FeeSide.ORIGINAL -> fair
                            FeeSide.CONVERTED -> {
                                val stack =
                                    FeeCalculator.totalStack(
                                        feeList.orEmpty(),
                                        baseCurrency,
                                        destinationCurrency,
                                        exchangeId,
                                        bankId,
                                    )
                                if (stack.compareTo(BigDecimal.ZERO) == 0) {
                                    fair
                                } else {
                                    fair.divide(stack, MathContext.DECIMAL128)
                                }
                            }
                        }
                    this.value = displayed.toPlainString()
                }
            }
        }

    /**
     * Public accessor for the fee stack factor of an arbitrary pair,
     * used by ad-hoc UIs (e.g. the quick-conversions popup) that need to
     * apply fees outside the main result pipeline.
     */
    internal fun feeStackFor(
        base: Currency?,
        dest: Currency?,
    ): BigDecimal =
        FeeCalculator.totalStack(
            fees.value.orEmpty(),
            base,
            dest,
            activeExchangeId.value,
            activeBankId.value,
        )

    /**
     * The combined multiplicative fee factor for the current pair.
     * Exposed so the UI can render a hint (e.g. badge on the side toggle)
     * when at least one fee is active for the current currencies.
     */
    private val totalStack =
        object : MediatorLiveData<BigDecimal>() {
            var feeList: List<Fee>? = null
            var base: Currency? = null
            var dest: Currency? = null
            var exchangeId: String? = null
            var bankId: String? = null

            init {
                addSource(fees) {
                    feeList = it
                    update()
                }
                addSource(currentBaseCurrency) {
                    base = it
                    update()
                }
                addSource(currentDestinationCurrency) {
                    dest = it
                    update()
                }
                addSource(activeExchangeId) {
                    exchangeId = it
                    update()
                }
                addSource(activeBankId) {
                    bankId = it
                    update()
                }
            }

            private fun update() {
                this.value = FeeCalculator.totalStack(feeList.orEmpty(), base, dest, exchangeId, bankId)
            }
        }

    /**
     * The additional "true cost" on the input side when [FeeSide.ORIGINAL]
     * is active and at least one fee applies: `input * totalStack`. `null`
     * when the total stack is trivial (== 1) or the side is [FeeSide.CONVERTED].
     */
    private val trueCost =
        object : MediatorLiveData<BigDecimal?>() {
            var amount: BigDecimal = BigDecimal.ZERO
            var stack: BigDecimal = BigDecimal.ONE
            var side: FeeSide = FeeSide.ORIGINAL
            var feeList: List<Fee> = emptyList()

            init {
                addSource(getCurrentBaseValueAsNumber()) {
                    amount = it ?: BigDecimal.ZERO
                    update()
                }
                addSource(totalStack) {
                    stack = it ?: BigDecimal.ONE
                    update()
                }
                addSource(feeSide) {
                    side = it ?: FeeSide.ORIGINAL
                    update()
                }
                addSource(fees) {
                    feeList = it.orEmpty()
                    update()
                }
            }

            private fun update() {
                this.value =
                    when {
                        side != FeeSide.ORIGINAL -> null
                        feeList.isEmpty() -> null
                        stack.compareTo(BigDecimal.ONE) == 0 -> null
                        else -> amount.multiply(stack, MathContext.DECIMAL128)
                    }
            }
        }

    /**
     * See [trueCost].
     */
    internal fun getTrueCost(): LiveData<BigDecimal?> = trueCost

    /**
     * The undiscounted (fair) destination amount when [FeeSide.CONVERTED]
     * is active and at least one fee applies: `result * totalStack`. `null`
     * when the total stack is trivial (== 1) or the side is [FeeSide.ORIGINAL].
     */
    private val originalValue =
        object : MediatorLiveData<BigDecimal?>() {
            var resultVal: BigDecimal = BigDecimal.ZERO
            var stack: BigDecimal = BigDecimal.ONE
            var side: FeeSide = FeeSide.ORIGINAL
            var feeList: List<Fee> = emptyList()

            init {
                addSource(getResultAsNumber()) {
                    resultVal = it ?: BigDecimal.ZERO
                    update()
                }
                addSource(totalStack) {
                    stack = it ?: BigDecimal.ONE
                    update()
                }
                addSource(feeSide) {
                    side = it ?: FeeSide.ORIGINAL
                    update()
                }
                addSource(fees) {
                    feeList = it.orEmpty()
                    update()
                }
            }

            private fun update() {
                this.value =
                    when {
                        side != FeeSide.CONVERTED -> null
                        feeList.isEmpty() -> null
                        stack.compareTo(BigDecimal.ONE) == 0 -> null
                        else -> resultVal.multiply(stack, MathContext.DECIMAL128)
                    }
            }
        }

    /**
     * See [originalValue].
     */
    internal fun getOriginalValue(): LiveData<BigDecimal?> = originalValue

    /**
     * The combined multiplicative fee factor for the current pair.
     */
    internal fun getTotalStack(): LiveData<BigDecimal> = totalStack

    /**
     * the total destination value, as BigDecimal (internal is string)
     */
    internal fun getResultAsNumber(): LiveData<BigDecimal> =
        result.map {
            it?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        }

    /**
     * the nicely formatted, total destination value including the currency symbol at the right position.
     */
    internal fun getResultFormatted(): LiveData<SpannableStringBuilder> =
        object : MediatorLiveData<SpannableStringBuilder>() {
            var resultText: String? = null
            var currency: Currency? = null
            var places: Int = 2

            init {
                addSource(result) {
                    resultText = it
                    update()
                }
                addSource(currentDestinationCurrency) {
                    currency = it
                    update()
                }
                addSource(decimalPlaces) {
                    places = it
                    update()
                }
            }

            fun update() {
                val number = (
                    resultText?.toHumanReadableNumber(
                        app,
                        trim = true,
                        decimalPlaces = places,
                    ) ?: "0"
                )
                this.value = buildBoldNumberWithSymbol(number, currency?.symbol())
            }
        }

    /**
     * the current decimal-places preference, for output-side rounding.
     */
    internal fun getDecimalPlaces(): LiveData<Int> = decimalPlaces

    /*
     * user input **********************************************************************************
     */

    internal fun addNumber(value: String) = input.addNumber(value)

    internal fun paste(value: Number) = input.paste(value)

    internal fun addPercent() = input.addPercent()

    internal fun addDecimal() = input.addDecimal()

    internal fun delete() = input.delete()

    internal fun clear() = input.clear()

    internal fun addition() = input.addOperator(OPERATOR_PLUS)

    internal fun subtraction() = input.addOperator(OPERATOR_MINUS)

    internal fun multiplication() = input.addOperator(OPERATOR_MULTIPLY)

    internal fun division() = input.addOperator(OPERATOR_DIVIDE)

    internal fun openParen() = input.addOpenParen()

    internal fun closeParen() = input.addCloseParen()

    // Cycle-toggle for the shared `()` keypad button — inserts whichever
    // glyph is currently highlighted.
    internal fun applyNextParen() = input.applyNextParen()

    // Raw as-typed expression for seeding the system-IME EditText on mode
    // switch. Display glyphs (× ÷ −) are normalised back to ASCII so the seed
    // matches what the user's keyboard produces. An untouched state (base
    // still "0", no calc row) seeds as empty — otherwise the leading "0"
    // gets prepended to the next keystroke and the EditText drifts out of
    // sync with the calculator state.
    internal fun currentTypedExpression(): String {
        input.calculationValueText.value?.let { return it.normaliseGlyphsToAscii() }
        val base = input.baseValueText.value.orEmpty()
        return if (base == "0") "" else base.normaliseGlyphsToAscii()
    }

    // Which paren the cycle-toggle keypad button should insert next — drives
    // the bold/green vs grey highlight on the two-glyph `()` button.
    internal fun nextParen(): LiveData<Char> = input.nextParen

    /*
     * selected currencies *************************************************************************
     */

    internal fun setBaseCurrency(currency: Currency) {
        db.saveLastUsedRates(
            currency,
            currentDestinationCurrency.value,
        )
        prefetchTimeline(currency, currentDestinationCurrency.value)
    }

    internal fun setDestinationCurrency(currency: Currency) {
        db.saveLastUsedRates(
            currentBaseCurrency.value,
            currency,
        )
        prefetchTimeline(currentBaseCurrency.value, currency)
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

    internal fun getBaseCurrency(): LiveData<Currency?> = currentBaseCurrency

    internal fun getDestinationCurrency(): LiveData<Currency?> = currentDestinationCurrency

    /*
     * historical rates
     */

    internal fun setHistoricalDate(date: LocalDate?) {
        // check if previous date was "latest" or historical
        val wasLatestActive = db.getHistoricalDate() == null
        // save selected historical date to db
        db.setHistoricalDate(date)
        // refresh, if new date != cached date or if last state was "latest"
        if (date != db.getDate() || wasLatestActive) {
            forceUpdateExchangeRate()
        }
    }

    internal fun getHistoricalDate(): LocalDate? = db.getHistoricalDate()

    internal fun getHistoricalLiveDate(): LiveData<LocalDate?> = db.getHistoricalLiveDate()

    /*
     * helpers =====================================================================================
     */

    private fun isInCalculationMode(): Boolean = input.isInCalculationMode()
}
