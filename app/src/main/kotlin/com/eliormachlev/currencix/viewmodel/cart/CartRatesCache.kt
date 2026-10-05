package com.eliormachlev.currencix.viewmodel.cart

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeCalculator
import com.eliormachlev.currencix.repository.Database
import kotlinx.collections.immutable.ImmutableList
import java.math.BigDecimal

/**
 * Owns the fee list, exchange rates, and active-exchange/bank ids the cart's
 * fee-math needs. Each source is exposed both as a [LiveData] (for observers
 * that want to re-render on change) and as a last-known scalar so synchronous
 * callers — share snapshot, fee-line rendering, MediatorLiveData recomputes —
 * can read the current value without a suspend hop.
 *
 * The four `observeForever` subscriptions are unregistered from [clear],
 * which the owning view model should call from `onCleared`.
 */
class CartRatesCache(
    db: Database,
) {
    val fees: LiveData<ImmutableList<Fee>> = db.fees.getFees()
    val rates: LiveData<ExchangeRates?> = db.rates.getExchangeRates()

    var lastFees: List<Fee> = emptyList()
        private set
    var lastRates: ExchangeRates? = null
        private set
    var lastActiveExchangeId: String? = db.fees.getActiveExchangeIdBlocking()
        private set
    var lastActiveBankId: String? = db.fees.getActiveBankIdBlocking()
        private set

    private val activeExchange: LiveData<String?> = db.fees.getActiveExchangeId()
    private val activeBank: LiveData<String?> = db.fees.getActiveBankId()

    private val feesObserver = Observer<ImmutableList<Fee>> { lastFees = it }
    private val ratesObserver = Observer<ExchangeRates?> { lastRates = it }
    private val activeExchangeObserver = Observer<String?> { lastActiveExchangeId = it }
    private val activeBankObserver = Observer<String?> { lastActiveBankId = it }

    init {
        fees.observeForever(feesObserver)
        rates.observeForever(ratesObserver)
        activeExchange.observeForever(activeExchangeObserver)
        activeBank.observeForever(activeBankObserver)
    }

    fun clear() {
        fees.removeObserver(feesObserver)
        rates.removeObserver(ratesObserver)
        activeExchange.removeObserver(activeExchangeObserver)
        activeBank.removeObserver(activeBankObserver)
    }

    /**
     * Freeze all four last-known scalars into a single snapshot. Callers that
     * need coherent fee/rate state (e.g. the share flow, which reads several
     * fields in sequence) should snapshot once and reuse the frozen values so
     * a mid-flow refresh can't mix new fees with old rates.
     */
    fun snapshot(): CartRatesSnapshot =
        CartRatesSnapshot(
            fees = lastFees,
            rates = lastRates,
            activeExchangeId = lastActiveExchangeId,
            activeBankId = lastActiveBankId,
        )

    /** Multiplicative fee stack against the current active exchange/bank ids. */
    fun feeStackFor(
        base: Currency?,
        dest: Currency?,
    ): BigDecimal = snapshot().feeStackFor(base, dest)
}

/**
 * Frozen view over [CartRatesCache]'s scalars. Same shape as the underlying
 * fields — mirrors them so callers can compute against a coherent set even if
 * the cache refreshes mid-computation.
 */
data class CartRatesSnapshot(
    val fees: List<Fee>,
    val rates: ExchangeRates?,
    val activeExchangeId: String?,
    val activeBankId: String?,
) {
    fun feeStackFor(
        base: Currency?,
        dest: Currency?,
    ): BigDecimal = FeeCalculator.feeStack(fees, base, dest, activeExchangeId, activeBankId)
}
