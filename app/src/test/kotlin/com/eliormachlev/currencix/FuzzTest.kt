package com.eliormachlev.currencix

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.PriceParser
import com.eliormachlev.currencix.util.ReceiptParser
import com.eliormachlev.currencix.util.evaluateCalculatorExpression
import com.eliormachlev.currencix.util.parseDecimal
import com.eliormachlev.currencix.view.main.ConverterLaunch

// Fuzzed in CI (build.yaml's fuzz job: -PfuzzTests, JAZZER_FUZZ=1, one
// target per run) for FUZZ_DURATION each; without JAZZER_FUZZ only the saved
// inputs are replayed. A crash fails the run and saves its input under
// src/test/resources, where it's replayed from then on.
private const val FUZZ_DURATION = "2m"

// The converter's own intent actions, and how much of an unknown one to take.
private val LAUNCH_ACTIONS = listOf(ConverterLaunch.ACTION_CONVERT, ConverterLaunch.ACTION_OPEN_CART, ConverterLaunch.ACTION_OPEN_CHART)
private const val LAUNCH_EXTRAS = 3
private const val MAX_FUZZ_ACTION_LENGTH = 64
private const val MAX_FUZZ_EXTRA_LENGTH = 64

/**
 * The parsers that take text the app doesn't control, each of which must
 * answer (null, nothing, "0") rather than throw on anything it's given:
 * a currency code, text selected in another app (Convert), a receipt's
 * scanned text, a calculation (typed, or in an imported cart), a typed
 * amount, and another app's intent to the exported converter.
 */
class FuzzTest {
    @FuzzTest(maxDuration = FUZZ_DURATION)
    fun fuzzCurrencyFromString(data: FuzzedDataProvider) {
        Currency.fromString(data.consumeRemainingAsString())
    }

    @FuzzTest(maxDuration = FUZZ_DURATION)
    fun fuzzSelectedPrice(data: FuzzedDataProvider) {
        PriceParser.parse(data.consumeRemainingAsString())
    }

    @FuzzTest(maxDuration = FUZZ_DURATION)
    fun fuzzReceiptText(data: FuzzedDataProvider) {
        ReceiptParser.parse(data.consumeRemainingAsString())
    }

    @FuzzTest(maxDuration = FUZZ_DURATION)
    fun fuzzCalculation(data: FuzzedDataProvider) {
        data.consumeRemainingAsString().evaluateCalculatorExpression()
    }

    @FuzzTest(maxDuration = FUZZ_DURATION)
    fun fuzzTypedAmount(data: FuzzedDataProvider) {
        parseDecimal(data.consumeRemainingAsString())
    }

    @FuzzTest(maxDuration = FUZZ_DURATION)
    fun fuzzLaunchIntent(data: FuzzedDataProvider) {
        // Mostly the app's own actions, so the extras get read; sometimes anything.
        val action = if (data.consumeBoolean()) data.pickValue(LAUNCH_ACTIONS) else data.consumeString(MAX_FUZZ_ACTION_LENGTH)
        val extras = List(LAUNCH_EXTRAS) { data.consumeString(MAX_FUZZ_EXTRA_LENGTH) }
        ConverterLaunch.parse(action) { name -> extras[name.hashCode().mod(LAUNCH_EXTRAS)] }
    }
}
