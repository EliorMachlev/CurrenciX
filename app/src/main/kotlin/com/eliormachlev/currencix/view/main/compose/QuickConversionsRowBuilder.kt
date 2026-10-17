package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.rateFor
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.toHumanReadableNumber
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private val QUICK_AMOUNTS: List<BigDecimal> = listOf("1", "5", "10", "20", "50", "100", "500", "1000").map(::BigDecimal)
private const val ROW_DEFAULT_DECIMALS = 2
private const val ROW_SMALL_AMOUNT_DECIMALS = 4
private val ROW_SMALL_AMOUNT_THRESHOLD: BigDecimal = BigDecimal.ONE

// Fee percent shown on a row ("+ 1%"), as on the converter's fee chip.
private const val FEE_PERCENT_DECIMAL_PLACES = 2

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
    // The amount in the converter: a row of its own, in order, when it isn't
    // one of the round amounts already.
    val typed: BigDecimal? = null,
)

/**
 * The pair at round amounts, plus the typed one, smallest first: the bare
 * numbers for the sheet's table, and each as "ILS ₪ 1 = USD $ 0.3249" for
 * copy, share and TalkBack. With a fee in force each row adds what it comes
 * to with the fee, "+ 1% = USD $ 0.3281": the converter's True Cost
 * (result x fee stack), in the result's currency.
 */
fun buildQuickConversionRows(inputs: QuickConversionRowInputs): List<QuickConversionsRow> {
    val baseRate = inputs.rates.rateFor(inputs.from)?.value ?: return emptyList()
    val destRate = inputs.rates.rateFor(inputs.to)?.value ?: return emptyList()
    val feePercent = inputs.feeStack.takeUnless { it.isNeutralFeeStack() }?.let { feePercentLabel(inputs.ctx, it) }
    val typed = inputs.typed?.takeIf { it.signum() > 0 && QUICK_AMOUNTS.none { round -> round.compareTo(it) == 0 } }
    return (QUICK_AMOUNTS + listOfNotNull(typed)).sorted().map { amount ->
        val fair = amount.divide(baseRate, MathContext.DECIMAL128).multiply(destRate)
        // "1,000", grouped as the results are.
        val fromValue = amount.stripTrailingZeros().toPlainString().toHumanReadableNumber(inputs.ctx)
        val toValue = fair.formatForRow(inputs.ctx)
        val withFeeValue = feePercent?.let { fair.multiply(inputs.feeStack, MathContext.DECIMAL128).formatForRow(inputs.ctx) }
        QuickConversionsRow(
            amountFromText = moneyLabel(inputs.from, fromValue),
            amountToText = moneyLabel(inputs.to, toValue),
            costWithFeeText = withFeeValue?.let { "$feePercent = ${moneyLabel(inputs.to, it)}" },
            amount = amount,
            isTyped = typed != null && amount.compareTo(typed) == 0,
            fromValue = fromValue,
            toValue = toValue,
            withFeeValue = withFeeValue,
            feePercent = feePercent,
        )
    }
}

// "ILS ₪ 1": the code, its symbol when it has one, then the amount.
private fun moneyLabel(
    currency: Currency,
    amount: String,
): String {
    val iso = currency.iso4217Alpha()
    val symbol = currency.symbolOrIso().takeIf { it != iso }
    return listOfNotNull(iso, symbol, amount).joinToString(" ")
}

// "+ 1%", "- 0.5%": the fee stack's change, signed apart from the number.
private fun feePercentLabel(
    ctx: Context,
    feeStack: BigDecimal,
): String {
    val percent = feeStack.feePercentDelta(FEE_PERCENT_DECIMAL_PLACES, RoundingMode.HALF_UP)
    val sign = if (percent.signum() < 0) "-" else "+"
    return "$sign ${percent.abs().toHumanReadableNumber(ctx, trim = true)}%"
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
