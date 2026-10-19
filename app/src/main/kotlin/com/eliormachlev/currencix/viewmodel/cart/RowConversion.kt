package com.eliormachlev.currencix.viewmodel.cart

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.convert
import java.math.BigDecimal

/**
 * How a cart row's price reads in the destination [currency]: [convert] at
 * the cached rate, before fees (the footer's total adds those). Null for a
 * cart priced in the currency it's converted to, or before rates exist.
 */
class RowConversion(
    val currency: Currency,
    val convert: (BigDecimal) -> BigDecimal?,
) {
    companion object {
        internal fun of(
            cart: SavedCart?,
            rates: ExchangeRates?,
        ): RowConversion? {
            cart ?: return null
            rates ?: return null
            val (base, dest) = cart.resolvedPair()
            return if (base == dest) null else RowConversion(dest) { amount -> rates.convert(amount, base, dest) }
        }
    }
}
