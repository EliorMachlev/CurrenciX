package com.eliormachlev.currencix.viewmodel.cart

import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeCalculator
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.util.evaluateCalculatorExpression
import com.eliormachlev.currencix.util.isNeutralFeeStack
import java.math.BigDecimal
import java.math.MathContext

/**
 * Evaluate a cart row's expression string to a numeric amount. Empty and
 * malformed expressions collapse to zero so a partially-typed row never
 * derails a totals calculation.
 */
fun evaluateItem(item: CartItem): BigDecimal {
    val raw = item.expression.trim()
    if (raw.isEmpty()) return BigDecimal.ZERO
    return runCatching { raw.evaluateCalculatorExpression().toBigDecimal() }
        .getOrDefault(BigDecimal.ZERO)
}

/** Sum every row's evaluated value in the cart's base currency. */
internal fun subtotalOf(cart: SavedCart?): BigDecimal {
    cart ?: return BigDecimal.ZERO
    return cart.items.fold(BigDecimal.ZERO) { acc, item -> acc + evaluateItem(item) }
}

/**
 * Total in the destination currency: subtotal → converted at [rates] →
 * reduced by the CONVERTED-side fee stack from [feeList]. ORIGINAL-side
 * fees don't change the displayed total; they surface separately as "true
 * cost" on the base side.
 */
internal fun totalOf(
    cart: SavedCart?,
    feeList: List<Fee>,
    rates: ExchangeRates?,
    activeExchangeId: String?,
    activeBankId: String?,
): BigDecimal {
    cart ?: return BigDecimal.ZERO
    val (base, dest) = cart.resolvedPair()
    val subtotal = subtotalOf(cart)
    val converted = convertAmount(subtotal, base, dest, rates)
    val stacks = FeeCalculator.sideStacks(feeList, base, dest, activeExchangeId, activeBankId)
    return applyConvertedStack(converted, stacks.converted)
}

/**
 * Persisted ISO codes are strings, so unknown values (legacy carts,
 * imported files) can slip through and return `null` from
 * [Currency.fromString]. Fall back to USD in that case so downstream math
 * and UI stay non-null instead of exploding on an edge case.
 */
internal fun resolveCurrency(iso: String): Currency = Currency.fromString(iso) ?: Currency.USD

// A cart's persisted currency pair, resolved once (both ISO strings become
// [Currency]s) with the "unset destination collapses to base" fallback. Every
// pipeline stage — fee stack, share snapshot, total math — needs this shape.
internal fun SavedCart.resolvedPair(): Pair<Currency, Currency> {
    val base = resolveCurrency(currency)
    val dest = destinationCurrency?.let { resolveCurrency(it) } ?: base
    return base to dest
}

/**
 * Convert [amount] from [base] to [dest] using cached rates. Returns
 * [amount] unchanged when base == dest or when the pair's rate is missing —
 * the latter avoids showing "0" while rates trickle in.
 */
internal fun convertAmount(
    amount: BigDecimal,
    base: Currency,
    dest: Currency,
    rates: ExchangeRates?,
): BigDecimal {
    if (base == dest) return amount
    val baseRate = rates?.rates?.find { it.currency == base }?.value ?: return amount
    val destRate = rates.rates.find { it.currency == dest }?.value ?: return amount
    return amount.divide(baseRate, MathContext.DECIMAL128).multiply(destRate)
}

/**
 * Fold the CONVERTED-side [convertedStack] into [converted]. A neutral
 * stack (no CONVERTED-side fees) leaves the destination amount alone;
 * otherwise divide so the fee reduces what you'd actually receive.
 * ORIGINAL-side fees don't participate here — they surface as "true cost"
 * on the input side instead.
 */
internal fun applyConvertedStack(
    converted: BigDecimal,
    convertedStack: BigDecimal,
): BigDecimal {
    if (convertedStack.isNeutralFeeStack()) return converted
    return converted.divide(convertedStack, MathContext.DECIMAL128)
}
