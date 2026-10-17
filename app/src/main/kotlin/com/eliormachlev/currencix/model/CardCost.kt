package com.eliormachlev.currencix.model

import java.math.BigDecimal
import java.math.MathContext

/** One bank / card fee priced for a conversion: its whole fee stack, and what the amount comes to with it. */
data class CardCost(
    val card: Fee.GlobalBank,
    // Every fee that applies with this card, multiplied out (1.025 for 2.5%).
    val stack: BigDecimal,
    // The converted amount with that stack; null when there's no amount to price.
    val total: BigDecimal?,
)

/**
 * The user's bank / card fees side by side for converting [amount] (already
 * in [dest], fee-free) from [base]: each card with the pair's own fees and
 * the exchange fee in force, cheapest first. Empty with fewer than two
 * active cards, since there's nothing to compare.
 */
fun compareCards(
    fees: List<Fee>,
    base: Currency?,
    dest: Currency?,
    activeExchangeId: String?,
    amount: BigDecimal?,
): List<CardCost> {
    val cards = FeeCalculator.applicableFees(fees, base, dest).filterIsInstance<Fee.GlobalBank>()
    if (cards.size < 2) return emptyList()
    val others = FeeCalculator.activeFees(fees.filterNot { it is Fee.GlobalBank }, base, dest, activeExchangeId)
    val priced = amount?.takeIf { it.signum() > 0 }
    return cards
        .map { card ->
            val stack = FeeCalculator.stackFactor(others + card)
            CardCost(card, stack, priced?.multiply(stack, MathContext.DECIMAL128))
        }.sortedBy { it.stack }
}
