package com.eliormachlev.currencix.model

import java.math.BigDecimal
import java.math.MathContext

// The tiles' round amounts in a currency worth about a US dollar; scaled for
// the rest by [quickAmountScale].
private val BASE_AMOUNTS: List<BigDecimal> = listOf("1", "5", "10", "20", "50", "100", "500", "1000").map(::BigDecimal)

private val TWO = BigDecimal(2)

// The currencies a "dollar's worth" is measured in, the first one the rates have.
private val REFERENCE_CURRENCIES = listOf(Currency.USD, Currency.EUR)

/** How many of [from] are worth about one US dollar, as a power of ten (at least 1): 1 for EUR or ILS, 100 for JPY, 1,000 for KRW. */
fun quickAmountScale(
    from: Currency,
    rates: ExchangeRates,
): BigDecimal {
    val fromRate = rates.rateFor(from)?.value ?: return BigDecimal.ONE
    val referenceRate = REFERENCE_CURRENCIES.firstNotNullOfOrNull { rates.rateFor(it)?.value } ?: return BigDecimal.ONE
    if (referenceRate.signum() <= 0) return BigDecimal.ONE
    // Units of [from] per reference unit; its leading digit's place is the power of ten.
    val perReference = fromRate.divide(referenceRate, MathContext.DECIMAL64)
    if (perReference < BigDecimal.TEN) return BigDecimal.ONE
    val exponent = perReference.precision() - perReference.scale() - 1
    return BigDecimal.TEN.pow(exponent)
}

/**
 * The quick conversions' amounts in [from], smallest first: the user's own
 * for that currency ([custom]) when there are any, else the round amounts
 * scaled to it, so KRW starts at 1,000 rather than 1.
 */
fun quickAmounts(
    from: Currency,
    rates: ExchangeRates,
    custom: Map<Currency, List<BigDecimal>>,
): List<BigDecimal> =
    custom[from]?.takeIf { it.isNotEmpty() }?.sorted()
        ?: quickAmountScale(from, rates).let { scale -> BASE_AMOUNTS.map { it * scale } }

/** One more round amount after [amounts] (twice the largest), to fill a line of tiles. */
fun quickFillAmount(amounts: List<BigDecimal>): BigDecimal = (amounts.maxOrNull() ?: BigDecimal.ONE) * TWO

/**
 * The user's own amounts for [currency] after one change: [old] (one of
 * [current], or null to add) becomes [new]; no duplicates, smallest first.
 */
fun Map<Currency, List<BigDecimal>>.withQuickAmount(
    currency: Currency,
    current: List<BigDecimal>,
    old: BigDecimal?,
    new: BigDecimal,
): Map<Currency, List<BigDecimal>> {
    val kept = current.filterNot { old != null && it.compareTo(old) == 0 }
    val amounts =
        (kept + new)
            .distinctBy { it.stripTrailingZeros() }
            .sorted()
            .take(MAX_QUICK_AMOUNTS)
    return this + (currency to amounts)
}

// Stored as "KRW:1000,5000;JPY:100,500".
private const val ENTRY_SEPARATOR = ";"
private const val CODE_SEPARATOR = ":"
private const val AMOUNT_SEPARATOR = ","

/** At most this many of the user's own amounts per currency. */
const val MAX_QUICK_AMOUNTS = 16

// Amounts from here up are no amount anyone converts: skipped.
private val MAX_QUICK_AMOUNT = BigDecimal("1E15")

/** The user's own amounts as one stored string; null when there are none. */
fun encodeQuickAmounts(custom: Map<Currency, List<BigDecimal>>): String? =
    custom
        .filterValues { it.isNotEmpty() }
        .entries
        .joinToString(ENTRY_SEPARATOR) { (currency, amounts) ->
            currency.iso4217Alpha() + CODE_SEPARATOR + amounts.joinToString(AMOUNT_SEPARATOR) { it.stripTrailingZeros().toPlainString() }
        }.ifEmpty { null }

/**
 * Read back leniently: unknown currencies, unreadable amounts and amounts
 * not above zero (or absurdly large) are skipped, duplicates
 * dropped, and each list kept to [MAX_QUICK_AMOUNTS].
 */
fun decodeQuickAmounts(stored: String?): Map<Currency, List<BigDecimal>> =
    stored
        .orEmpty()
        .split(ENTRY_SEPARATOR)
        .mapNotNull { entry ->
            val currency = Currency.fromString(entry.substringBefore(CODE_SEPARATOR).trim()) ?: return@mapNotNull null
            val amounts =
                entry
                    .substringAfter(CODE_SEPARATOR, "")
                    .split(AMOUNT_SEPARATOR)
                    .mapNotNull { it.trim().toBigDecimalOrNull()?.takeIf(::isQuickAmount) }
                    .distinctBy { it.stripTrailingZeros() }
                    .sorted()
                    .take(MAX_QUICK_AMOUNTS)
            amounts.takeIf { it.isNotEmpty() }?.let { currency to it }
        }.toMap()

/** Whether [amount] can be one of the tiles': above zero and below a quadrillion. */
fun isQuickAmount(amount: BigDecimal): Boolean = amount.signum() > 0 && amount < MAX_QUICK_AMOUNT
