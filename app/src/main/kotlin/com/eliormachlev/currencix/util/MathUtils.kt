package com.eliormachlev.currencix.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private val SIGNIFICANT_THRESHOLD = BigDecimal("0.01")

/** 100 %: a percentage divided by this is a fraction. */
val HUNDRED_PERCENT: BigDecimal = BigDecimal(100)

/** This percentage as a fraction (12.5 → 0.125). */
fun BigDecimal.percentToFraction(): BigDecimal = divide(HUNDRED_PERCENT, MathContext.DECIMAL128)

fun calculateDifference(
    old: BigDecimal?,
    new: BigDecimal?,
): BigDecimal? =
    if (old == null || new == null || old.compareTo(BigDecimal.ZERO) == 0) {
        null
    } else {
        (new - old).divide(old, MathContext.DECIMAL128) * HUNDRED_PERCENT
    }

// A multiplicative fee stack of 1.0 means "no fees apply". Centralised so the
// main screen, quick-conversions dialog, and cart all gate on the same
// predicate instead of open-coding `compareTo(BigDecimal.ONE) == 0`.
fun BigDecimal.isNeutralFeeStack(): Boolean = compareTo(BigDecimal.ONE) == 0

// Additive delta of a multiplicative fee stack: `stack - 1`. Signed —
// positive for a markup, negative for a discount. `base * delta` gives the
// fee amount itself; used by both the money-facing renderers and the
// percent-facing [feePercentDelta].
fun BigDecimal.feeStackDelta(): BigDecimal = subtract(BigDecimal.ONE)

// Convert a multiplicative fee stack (e.g. `1.025`) to its percentage delta
// (e.g. `2.50`). Callers vary the rounding mode: HALF_EVEN for money-facing
// totals (main, cart), HALF_UP for user-visible "fees applied" labels that
// should never round toward zero.
fun BigDecimal.feePercentDelta(
    scale: Int = 2,
    roundingMode: RoundingMode = RoundingMode.HALF_EVEN,
): BigDecimal =
    feeStackDelta()
        .multiply(HUNDRED_PERCENT, MathContext.DECIMAL128)
        .setScale(scale, roundingMode)

fun BigDecimal.getSignificantDecimalPlaces(significantNumbers: Int = 2): Int {
    if (this.abs() >= SIGNIFICANT_THRESHOLD) {
        return significantNumbers
    }
    val decimalStr = this.abs().stripTrailingZeros().toPlainString()
    val decimalPart = decimalStr.substringAfter('.', "")
    // find leading zeros
    val leadingZeros = decimalPart.takeWhile { it == '0' }.length
    // take x significant numbers after leading zeros
    val significantDigits = decimalPart.drop(leadingZeros).take(significantNumbers).length
    return leadingZeros + significantDigits
}
