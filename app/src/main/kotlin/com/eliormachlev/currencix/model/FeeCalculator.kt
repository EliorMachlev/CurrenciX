package com.eliormachlev.currencix.model

import java.math.BigDecimal
import java.math.MathContext

private val PERCENTAGE_DIVISOR = BigDecimal("100")

/**
 * Pure fee-stacking math. Extracted from MainViewModel so it can be exercised
 * without an Android [android.app.Application] context.
 *
 * Inactive fees ([Fee.isActive] == false) are always skipped — the flag lets
 * the user temporarily silence an entry without deleting it.
 *
 * Global exchange and global bank/card fees are **single-select**: at most one
 * of each participates in the stack, chosen by the picker IDs (falling back to
 * the first active entry of that category when the pick is unset or stale).
 * Specific-pair fees still stack together across every active match. Every fee
 * behaves as a source-side markup — the resulting stack inflates the input-side
 * "true cost" and leaves the displayed converted amount at the mid-market value.
 */
object FeeCalculator {
    /**
     * Return only those fees that apply for the given base/destination pair.
     * Global fees always apply; pair-specific fees match by ISO code, optionally
     * in both directions. Inactive entries are filtered out.
     */
    fun applicableFees(
        all: List<Fee>,
        base: Currency?,
        dest: Currency?,
    ): List<Fee> {
        val baseCode = base?.iso4217Alpha()
        val destCode = dest?.iso4217Alpha()
        return all.filter { fee -> fee.isActive && matchesPair(fee, baseCode, destCode) }
    }

    private fun matchesPair(
        fee: Fee,
        baseCode: String?,
        destCode: String?,
    ): Boolean =
        when (fee) {
            is Fee.GlobalExchange, is Fee.GlobalBank -> true
            is Fee.SpecificPair -> {
                if (baseCode == null || destCode == null) {
                    false
                } else if (fee.from == baseCode && fee.to == destCode) {
                    true
                } else {
                    fee.bothWays && fee.from == destCode && fee.to == baseCode
                }
            }
        }

    /**
     * Multiplicative stack factor for a subset of fees:
     * `product over subset of (1 + percent/100)`.
     */
    fun stackFactor(subset: List<Fee>): BigDecimal {
        var acc = BigDecimal.ONE
        subset.forEach { fee ->
            val delta = fee.percent.divide(PERCENTAGE_DIVISOR, MathContext.DECIMAL128)
            val factor = BigDecimal.ONE + delta
            acc = acc.multiply(factor, MathContext.DECIMAL128)
        }
        return acc
    }

    /**
     * Resolve the fees that actually participate for the given pair (specific
     * matches + single-select picks for global exchange/bank).
     */
    fun activeFees(
        all: List<Fee>,
        base: Currency?,
        dest: Currency?,
        activeExchangeId: String? = null,
        activeBankId: String? = null,
    ): List<Fee> {
        val applicable = applicableFees(all, base, dest)
        return applicable.filterIsInstance<Fee.SpecificPair>() +
            pickActive(applicable.filterIsInstance<Fee.GlobalExchange>(), activeExchangeId) +
            pickActive(applicable.filterIsInstance<Fee.GlobalBank>(), activeBankId)
    }

    /**
     * Multiplicative stack of every fee that participates for the given pair.
     */
    fun feeStack(
        all: List<Fee>,
        base: Currency?,
        dest: Currency?,
        activeExchangeId: String? = null,
        activeBankId: String? = null,
    ): BigDecimal = stackFactor(activeFees(all, base, dest, activeExchangeId, activeBankId))

    /**
     * Resolve the single-select choice: prefer the entry whose id matches
     * [activeId]; otherwise fall back to the first entry (preserving legacy
     * "one-and-only fee" behavior for users who haven't picked yet).
     * Returns a 0-or-1 element list so it can be passed straight to
     * [stackFactor].
     */
    private fun <T : Fee> pickActive(
        candidates: List<T>,
        activeId: String?,
    ): List<T> {
        if (candidates.isEmpty()) return emptyList()
        val chosen =
            activeId?.let { id -> candidates.firstOrNull { it.id == id } }
                ?: candidates.first()
        return listOf(chosen)
    }
}
