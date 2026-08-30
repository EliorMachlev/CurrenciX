package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.SideStacks
import com.eliormachlev.currencix.util.feeStackDelta
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

data class FeeRowPrefixes(
    val fee: String,
    val costWithFee: String,
    val valueBeforeFee: String,
    val reduction: String,
)

fun buildQuickConversionRows(
    ctx: Context,
    from: Currency,
    to: Currency,
    rates: ExchangeRates,
    sideStacks: SideStacks,
    prefixes: FeeRowPrefixes,
): List<QuickConversionsRow> {
    val baseRate = rates.rates?.find { it.currency == from }?.value ?: return emptyList()
    val destRate = rates.rates.find { it.currency == to }?.value ?: return emptyList()
    val hasOriginalFee = !sideStacks.original.isNeutralFeeStack()
    val hasConvertedFee = !sideStacks.converted.isNeutralFeeStack()
    val fromIso = from.iso4217Alpha()
    val toIso = to.iso4217Alpha()
    val fromMarker = from.symbolOrIso()
    val toMarker = to.symbolOrIso()
    val originalDelta = sideStacks.original.feeStackDelta()
    val convertedDelta = sideStacks.converted.feeStackDelta()
    return QUICK_AMOUNTS.map { amountStr ->
        val amt = BigDecimal(amountStr)
        val fair = amt.divide(baseRate, MathContext.DECIMAL128).multiply(destRate)
        val displayed =
            if (hasConvertedFee) {
                fair.divide(sideStacks.converted, MathContext.DECIMAL128)
            } else {
                fair
            }
        val originalFee =
            if (hasOriginalFee) {
                val fee = amt.multiply(originalDelta, MathContext.DECIMAL128).abs()
                prefixes.fee + ltrIsolate("${fee.formatForRow(ctx)} $fromMarker")
            } else {
                null
            }
        val costWithFee =
            if (hasOriginalFee) {
                val actual = amt.multiply(sideStacks.original, MathContext.DECIMAL128)
                prefixes.costWithFee + ltrIsolate("${actual.formatForRow(ctx)} $fromMarker")
            } else {
                null
            }
        val valueBeforeFee =
            if (hasConvertedFee) {
                prefixes.valueBeforeFee + ltrIsolate("${fair.formatForRow(ctx)} $toMarker")
            } else {
                null
            }
        val convertedFee =
            if (hasConvertedFee) {
                val fee = displayed.multiply(convertedDelta, MathContext.DECIMAL128).abs()
                prefixes.reduction + ltrIsolate("${fee.formatForRow(ctx)} $toMarker")
            } else {
                null
            }
        QuickConversionsRow(
            amountFromText = "$amountStr $fromIso",
            amountToText = "${displayed.formatForRow(ctx)} $toIso",
            originalFeeText = originalFee,
            costWithFeeText = costWithFee,
            valueBeforeFeeText = valueBeforeFee,
            convertedFeeText = convertedFee,
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
