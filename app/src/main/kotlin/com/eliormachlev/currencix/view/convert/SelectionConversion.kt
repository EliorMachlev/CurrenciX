package com.eliormachlev.currencix.view.convert

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.convert
import com.eliormachlev.currencix.util.PriceParser
import java.math.BigDecimal

/** What the text-selection popup shows for the selected text. */
sealed interface SelectionConversion {
    /**
     * [amount] of [from] is [result] of [to], at [rate] (1 [from] in [to]).
     * [assumedFrom] when the text named no currency and [from] is the
     * converter's own.
     */
    data class Converted(
        val amount: BigDecimal,
        val from: Currency,
        val to: Currency,
        val result: BigDecimal,
        val rate: BigDecimal,
        val assumedFrom: Boolean,
    ) : SelectionConversion

    /** Nothing that reads as a number. */
    data object NoAmount : SelectionConversion

    /** No cached rates yet, or none for this pair. */
    data object NoRates : SelectionConversion
}

/**
 * Converts the price in [text] into the converter's currency: into
 * [converterTo] normally, or into [converterFrom] when the price is already
 * in [converterTo] (a price in your destination currency is converted back).
 * A price with no currency is read as [converterFrom].
 */
fun convertSelection(
    text: String,
    rates: ExchangeRates?,
    converterFrom: Currency,
    converterTo: Currency,
): SelectionConversion {
    val price = PriceParser.parse(text, preferred = listOf(converterFrom, converterTo)) ?: return SelectionConversion.NoAmount
    val from = price.currency ?: converterFrom
    val to = targetCurrency(from, converterFrom, converterTo)
    val result = rates?.convert(price.amount, from, to) ?: return SelectionConversion.NoRates
    val rate = rates.convert(BigDecimal.ONE, from, to) ?: return SelectionConversion.NoRates
    return SelectionConversion.Converted(price.amount, from, to, result, rate, assumedFrom = price.currency == null)
}

/**
 * What a price in [from] converts into: the converter's [converterTo], or its
 * [converterFrom] when the price is already in [converterTo].
 */
fun targetCurrency(
    from: Currency,
    converterFrom: Currency,
    converterTo: Currency,
): Currency = if (from == converterTo) converterFrom else converterTo
