package com.eliormachlev.currencix.view.main.compose

import android.content.Context
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.quickAmounts
import com.eliormachlev.currencix.model.quickFillAmount
import com.eliormachlev.currencix.model.rateFor
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.moneyText
import com.eliormachlev.currencix.util.toHumanReadableNumber
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** The sheet shows its rows as tiles, this many to a line. */
internal const val TILES_PER_LINE = 2

// Fee percent shown on a row ("+1%"), as on the converter's fee chip.
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
    // The user's decimal places (Settings → Converter), as the converter
    // shows its result: every result in the table rounds to it.
    val decimalPlaces: Int,
    // The tiles' amounts in [from] ([quickAmounts]): the user's own, or the
    // round ones scaled to the currency.
    val amounts: List<BigDecimal> = quickAmounts(from, rates, emptyMap()),
)

/**
 * The pair at the tiles' amounts ([QuickConversionRowInputs.amounts]), plus
 * the typed one, smallest first (and twice the largest when that makes an odd
 * number, so the tiles pair up): the bare
 * numbers for the sheet's table, and each as "ILS ₪ 1 = USD $ 0.3249" for
 * copy, share and TalkBack. With a fee in force each row adds what it comes
 * to with the fee, "+1% = USD $ 0.3281": the converter's True Cost
 * (result x fee stack), in the result's currency.
 */
fun buildQuickConversionRows(inputs: QuickConversionRowInputs): List<QuickConversionsRow> {
    val baseRate = inputs.rates.rateFor(inputs.from)?.value ?: return emptyList()
    val destRate = inputs.rates.rateFor(inputs.to)?.value ?: return emptyList()
    val feePercent = inputs.feeStack.takeUnless { it.isNeutralFeeStack() }?.let { feePercentLabel(inputs.ctx, it) }
    val typed = inputs.typed?.takeIf { it.signum() > 0 && inputs.amounts.none { round -> round.compareTo(it) == 0 } }
    val amounts = inputs.amounts + listOfNotNull(typed)
    val filled = if (amounts.size % TILES_PER_LINE == 0) amounts else amounts + quickFillAmount(inputs.amounts)
    return filled.sorted().map { amount ->
        val fair = amount.divide(baseRate, MathContext.DECIMAL128).multiply(destRate)
        // "1,000", grouped as the results are.
        val fromValue = amount.stripTrailingZeros().toPlainString().toHumanReadableNumber(inputs.ctx)
        val toValue = fair.formatForRow(inputs.ctx, inputs.decimalPlaces)
        val withFeeValue =
            feePercent?.let {
                fair
                    .multiply(
                        inputs.feeStack,
                        MathContext.DECIMAL128,
                    ).formatForRow(inputs.ctx, inputs.decimalPlaces)
            }
        QuickConversionsRow(
            amountFromText = inputs.ctx.moneyLabel(inputs.from, fromValue),
            amountToText = inputs.ctx.moneyLabel(inputs.to, toValue),
            costWithFeeText = withFeeValue?.let { "$feePercent = ${inputs.ctx.moneyLabel(inputs.to, it)}" },
            amount = amount,
            isTyped = typed != null && amount.compareTo(typed) == 0,
            fromValue = fromValue,
            toValue = toValue,
            withFeeValue = withFeeValue,
            feePercent = feePercent,
        )
    }
}

// "ILS 1 ₪", "USD $ 0.32": the code, then the amount with its symbol in the
// language's writing order ([moneyText]: it's copied, shared and read out);
// just "CHF 1" when the code is all the currency has.
private fun Context.moneyLabel(
    currency: Currency,
    amount: String,
): String {
    val withSymbol = moneyText(amount, currency)
    return if (currency.symbol() == null) withSymbol else "${currency.iso4217Alpha()} $withSymbol"
}

// "+1%", "-0.5%": the fee stack's change, signed.
private fun feePercentLabel(
    ctx: Context,
    feeStack: BigDecimal,
): String {
    val percent = feeStack.feePercentDelta(FEE_PERCENT_DECIMAL_PLACES, RoundingMode.HALF_UP)
    val sign = if (percent.signum() < 0) "-" else "+"
    return "$sign${percent.abs().toHumanReadableNumber(ctx, trim = true)}%"
}

// Rounded to the user's [decimals], grouped and in the locale's separator.
private fun BigDecimal.formatForRow(
    ctx: Context,
    decimals: Int,
): String = setScale(decimals, RoundingMode.HALF_UP).toHumanReadableNumber(ctx)
