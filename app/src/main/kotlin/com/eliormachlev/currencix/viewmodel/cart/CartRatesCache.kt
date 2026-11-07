package com.eliormachlev.currencix.viewmodel.cart

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeCalculator
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.viewmodel.util.stateInEagerly
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal

/**
 * Owns the fee list, exchange rates, and active-exchange/bank ids the cart's
 * fee-math needs. Each source is a [StateFlow] collected for the whole of
 * [scope] (the owning view model's), so observers re-render on change and
 * synchronous callers — share snapshot, fee-line rendering, derived-state
 * recomputes — read the current value through `.value` without a suspend hop.
 */
class CartRatesCache(
    db: Database,
    scope: CoroutineScope,
) {
    val fees: StateFlow<ImmutableList<Fee>> =
        db.fees.fees.flow
            .stateInEagerly(scope, db.fees.fees.get())
    val rates: StateFlow<ExchangeRates?> = db.rates.getExchangeRates().stateInEagerly(scope, db.rates.getExchangeRatesBlocking())

    private val activeExchangeId: StateFlow<String?> =
        db.fees.activeExchangeId.flow
            .stateInEagerly(scope, db.fees.activeExchangeId.get())
    private val activeBankId: StateFlow<String?> =
        db.fees.activeBankId.flow
            .stateInEagerly(scope, db.fees.activeBankId.get())

    /**
     * Freeze all four current values into a single snapshot. Callers that
     * need coherent fee/rate state (e.g. the share flow, which reads several
     * fields in sequence) should snapshot once and reuse the frozen values so
     * a mid-flow refresh can't mix new fees with old rates.
     */
    fun snapshot(): CartRatesSnapshot =
        CartRatesSnapshot(
            fees = fees.value,
            rates = rates.value,
            activeExchangeId = activeExchangeId.value,
            activeBankId = activeBankId.value,
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
