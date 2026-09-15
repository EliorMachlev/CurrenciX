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

private val QUICK_AMOUNTS = listOf("1", "5", "10", "20", "50", "100", "500", "1000")
private const val ROW_DEFAULT_DECIMALS = 2
private const val ROW_SMALL_AMOUNT_DECIMALS = 4
private val ROW_SMALL_AMOUNT_THRESHOLD: BigDecimal = BigDecimal.ONE

/**
 * Bundle of everything [buildQuickConversionRows] needs to render its rows.
 * Grouped as a data class so the row builder's signature stays short and the
 * caller (the QuickConversions dialog) can pass a single snapshot instead of
 * six positional arguments.
 */
data class QuickConversionRowInputs(
    val ctx: Context,
    val from: Currency,
    val to: Currency,
    val rates: ExchangeRates,
    val feeStack: BigDecimal,
    val costWithFeePrefix: String,
)

fun buildQuickConversionRows(inputs: QuickConversionRowInputs): List<QuickConversionsRow> {
    val baseRate = inputs.rates.rateFor(inputs.from)?.value ?: return emptyList()
    val destRate = inputs.rates.rateFor(inputs.to)?.value ?: return emptyList()
    val hasOriginalFee = !inputs.feeStack.isNeutralFeeStack()
    val fromIso = inputs.from.iso4217Alpha()
    val toIso = inputs.to.iso4217Alpha()
    val fromMarker = inputs.from.symbolOrIso()
    return QUICK_AMOUNTS.map { amountStr ->
        val amt = BigDecimal(amountStr)
        val fair = amt.divide(baseRate, MathContext.DECIMAL128).multiply(destRate)
        val costWithFee =
            if (hasOriginalFee) {
                val actual = amt.multiply(inputs.feeStack, MathContext.DECIMAL128)
                inputs.costWithFeePrefix + ltrIsolate("${actual.formatForRow(inputs.ctx)} $fromMarker")
            } else {
                null
            }
        QuickConversionsRow(
            amountFromText = "$amountStr $fromIso",
            amountToText = "${fair.formatForRow(inputs.ctx)} $toIso",
            costWithFeeText = costWithFee,
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
