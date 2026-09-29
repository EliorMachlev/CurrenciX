package com.eliormachlev.currencix.viewmodel.cart

import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.convert
import com.eliormachlev.currencix.util.evaluateCalculatorExpression
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

/** The tip / tax on top of the items, in the base currency; zero without one. */
internal fun tipOf(cart: SavedCart?): BigDecimal {
    val percent = cart?.extras?.tipPercent ?: return BigDecimal.ZERO
    return subtotalOf(cart).multiply(percent).divide(PERCENT, MathContext.DECIMAL128)
}

/**
 * Items plus tip / tax, converted, before fees — the "fair" destination
 * amount the user *would* pay if the pipeline stopped here. The tip comes
 * before fees: a card's FX fee is charged on everything paid, tip included.
 */
internal fun convertedSubtotalOf(
    cart: SavedCart?,
    rates: ExchangeRates?,
): BigDecimal {
    cart ?: return BigDecimal.ZERO
    val (base, dest) = cart.resolvedPair()
    return convertAmount(subtotalOf(cart) + tipOf(cart), base, dest, rates)
}

/** Each person's share of [total] when the cart splits it [ways] ways. */
internal fun perPerson(
    total: BigDecimal,
    ways: Int,
): BigDecimal = total.divide(BigDecimal(ways.coerceAtLeast(1)), MathContext.DECIMAL128)

/** What's left of [budget] after [total]; negative when over. */
internal fun budgetLeft(
    total: BigDecimal,
    budget: BigDecimal,
): BigDecimal = budget - total

private val PERCENT = BigDecimal(100)

/**
 * Total in the destination currency: subtotal → converted at [rates] →
 * inflated by [feeStack]. Real-world FX fees are charged on the post-
 * conversion amount, so the fee multiplies the destination-side value.
 */
internal fun totalOf(
    cart: SavedCart?,
    rates: ExchangeRates?,
    feeStack: BigDecimal = BigDecimal.ONE,
): BigDecimal = convertedSubtotalOf(cart, rates).multiply(feeStack, MathContext.DECIMAL128)

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
): BigDecimal = rates?.convert(amount, base, dest) ?: amount
