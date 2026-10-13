package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.rateFor
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.ltrIsolate
import com.eliormachlev.currencix.util.toHumanReadableNumber
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private val QUICK_AMOUNTS: List<BigDecimal> = listOf("1", "5", "10", "20", "50", "100", "500", "1000").map(::BigDecimal)
private const val ROW_DEFAULT_DECIMALS = 2
private const val ROW_SMALL_AMOUNT_DECIMALS = 4
private val ROW_SMALL_AMOUNT_THRESHOLD: BigDecimal = BigDecimal.ONE

/**
 * Bundle of everything [buildQuickConversionRows] needs to render its rows.
 * Grouped as a data class so the row builder's signature stays short and the
 * caller (the QuickConversions sheet) can pass a single snapshot.
 */
data class QuickConversionRowInputs(
    val ctx: Context,
    val from: Currency,
    val to: Currency,
    val rates: ExchangeRates,
    val feeStack: BigDecimal,
    val costWithFeePrefix: String,
    // The amount in the converter: a row of its own, in order, when it isn't
    // one of the round amounts already.
    val typed: BigDecimal? = null,
)

/**
 * The pair at round amounts, plus the typed one, smallest first. With a fee
 * in force each result also says what it costs with the fee, in the result's
 * own currency, the converter's True Cost (result x fee stack).
 */
fun buildQuickConversionRows(inputs: QuickConversionRowInputs): List<QuickConversionsRow> {
    val baseRate = inputs.rates.rateFor(inputs.from)?.value ?: return emptyList()
    val destRate = inputs.rates.rateFor(inputs.to)?.value ?: return emptyList()
    val hasFee = !inputs.feeStack.isNeutralFeeStack()
    val fromIso = inputs.from.iso4217Alpha()
    val toIso = inputs.to.iso4217Alpha()
    val typed = inputs.typed?.takeIf { it.signum() > 0 && QUICK_AMOUNTS.none { round -> round.compareTo(it) == 0 } }
    return (QUICK_AMOUNTS + listOfNotNull(typed)).sorted().map { amount ->
        val fair = amount.divide(baseRate, MathContext.DECIMAL128).multiply(destRate)
        val withFee =
            if (hasFee) {
                val cost = fair.multiply(inputs.feeStack, MathContext.DECIMAL128)
                inputs.costWithFeePrefix + ltrIsolate("${cost.formatForRow(inputs.ctx)} $toIso")
            } else {
                null
            }
        QuickConversionsRow(
            amountFromText = "${amount.stripTrailingZeros().toPlainString()} $fromIso",
            amountToText = "${fair.formatForRow(inputs.ctx)} $toIso",
            costWithFeeText = withFee,
            amount = amount,
            isTyped = typed != null && amount.compareTo(typed) == 0,
        )
    }
}

private fun BigDecimal.formatForRow(ctx: Context): String {
    val decimals =
        if (this.abs() >= ROW_SMALL_AMOUNT_THRESHOLD) {
            ROW_DEFAULT_DECIMALS
        } else {
            ROW_SMALL_AMOUNT_DECIMALS
        }
    return this.setScale(decimals, RoundingMode.HALF_UP).toHumanReadableNumber(ctx)
}
