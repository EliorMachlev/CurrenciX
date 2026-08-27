package com.eliormachlev.currencix.model

import java.math.BigDecimal
import java.math.MathContext

private val PERCENTAGE_DIVISOR = BigDecimal("100")

/**
 * Pair of multiplicative fee stacks, split by which [FeeSide] each fee is
 * pinned to. [original] inflates the input-side "true cost"; [converted] is
 * folded into the destination amount the user sees.
 */
data class SideStacks(
    val original: BigDecimal,
    val converted: BigDecimal,
) {
    /** Product of both sides — the aggregate multiplicative impact on the rate. */
    val combined: BigDecimal = original.multiply(converted, MathContext.DECIMAL128)

    fun isNeutral(): Boolean =
        original.compareTo(BigDecimal.ONE) == 0 && converted.compareTo(BigDecimal.ONE) == 0

    companion object {
        val NEUTRAL = SideStacks(BigDecimal.ONE, BigDecimal.ONE)
    }
}

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
 * Specific-pair fees still stack together across every active match. Each fee
 * carries its own [FeeSide] which decides which of [SideStacks] it lands in.
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
     * `product over subset of (1 +/- percent/100)`.
     */
    fun stackFactor(subset: List<Fee>): BigDecimal {
        var acc = BigDecimal.ONE
        subset.forEach { fee ->
            val delta = fee.percent.divide(PERCENTAGE_DIVISOR, MathContext.DECIMAL128)
            val factor = if (fee.isMarkup) BigDecimal.ONE + delta else BigDecimal.ONE - delta
            acc = acc.multiply(factor, MathContext.DECIMAL128)
        }
        return acc
    }

    /**
     * Resolve which fees actually participate for the given pair (specific
     * matches + single-select picks for global exchange/bank), then split them
     * by [FeeSide] and return one multiplicative stack per side.
     */
    fun sideStacks(
        all: List<Fee>,
        base: Currency?,
        dest: Currency?,
        activeExchangeId: String? = null,
        activeBankId: String? = null,
    ): SideStacks {
        val applicable = applicableFees(all, base, dest)
        val chosen =
            applicable.filterIsInstance<Fee.SpecificPair>() +
                pickActive(applicable.filterIsInstance<Fee.GlobalExchange>(), activeExchangeId) +
                pickActive(applicable.filterIsInstance<Fee.GlobalBank>(), activeBankId)
        val (originals, converteds) = chosen.partition { it.feeSide == FeeSide.ORIGINAL }
        return SideStacks(stackFactor(originals), stackFactor(converteds))
    }

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
