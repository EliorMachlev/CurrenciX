package com.eliormachlev.currencix.viewmodel.cart

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeCalculator
import com.eliormachlev.currencix.model.SideStacks
import com.eliormachlev.currencix.repository.Database

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
    val fees: LiveData<List<Fee>> = db.getFees()
    val rates: LiveData<ExchangeRates?> = db.getExchangeRates()

    var lastFees: List<Fee> = emptyList()
        private set
    var lastRates: ExchangeRates? = null
        private set
    var lastActiveExchangeId: String? = db.getActiveExchangeIdBlocking()
        private set
    var lastActiveBankId: String? = db.getActiveBankIdBlocking()
        private set

    private val activeExchange: LiveData<String?> = db.getActiveExchangeId()
    private val activeBank: LiveData<String?> = db.getActiveBankId()

    private val feesObserver = Observer<List<Fee>> { lastFees = it }
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
}

fun CartRatesCache.sideStacksFor(
    base: Currency?,
    dest: Currency?,
): SideStacks = FeeCalculator.sideStacks(lastFees, base, dest, lastActiveExchangeId, lastActiveBankId)
