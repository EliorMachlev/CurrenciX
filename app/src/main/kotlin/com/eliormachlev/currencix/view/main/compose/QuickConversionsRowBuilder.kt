package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.FeeSide
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.ltrIsolate
import com.eliormachlev.currencix.util.toHumanReadableNumber
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private val QUICK_AMOUNTS = listOf("1", "5", "10", "20", "50", "100", "500", "1000")
private const val ROW_DEFAULT_DECIMALS = 2
private const val ROW_SMALL_AMOUNT_DECIMALS = 4
private val ROW_SMALL_AMOUNT_THRESHOLD: BigDecimal = BigDecimal.ONE

// A fee stack of exactly 0 is a "no fees configured" sentinel from the view
// model, distinct from the neutral 1.0 stack that means "fees cancelled to
// nothing". Both need to be treated as "no fees to apply".
fun BigDecimal.hasFees(): Boolean = compareTo(BigDecimal.ZERO) != 0 && !isNeutralFeeStack()

fun buildQuickConversionRows(
    ctx: Context,
    from: Currency,
    to: Currency,
    rates: ExchangeRates,
    feeStack: BigDecimal,
    feeSide: FeeSide,
    truePrefix: String,
    originalPrefix: String,
): List<QuickConversionsRow> {
    val baseRate = rates.rates?.find { it.currency == from }?.value ?: return emptyList()
    val destRate = rates.rates.find { it.currency == to }?.value ?: return emptyList()
    val hasFees = feeStack.hasFees()
    val fromIso = from.iso4217Alpha()
    val toIso = to.iso4217Alpha()
    return QUICK_AMOUNTS.map { amountStr ->
        val amt = BigDecimal(amountStr)
        val fair = amt.divide(baseRate, MathContext.DECIMAL128).multiply(destRate)
        val displayed =
            if (hasFees && feeSide == FeeSide.CONVERTED) {
                fair.divide(feeStack, MathContext.DECIMAL128)
            } else {
                fair
            }
        val trueCost =
            if (hasFees && feeSide == FeeSide.ORIGINAL) {
                val actual = amt.multiply(feeStack, MathContext.DECIMAL128)
                truePrefix + ltrIsolate("${actual.formatForRow(ctx)} $fromIso")
            } else {
                null
            }
        val originalValue =
            if (hasFees && feeSide == FeeSide.CONVERTED) {
                originalPrefix + ltrIsolate("${fair.formatForRow(ctx)} $toIso")
            } else {
                null
            }
        QuickConversionsRow(
            amountFromText = "$amountStr $fromIso",
            amountToText = "${displayed.formatForRow(ctx)} $toIso",
            trueCostText = trueCost,
            originalValueText = originalValue,
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
