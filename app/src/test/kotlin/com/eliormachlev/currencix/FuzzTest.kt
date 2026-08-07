package com.eliormachlev.currencix

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.toNumber

class FuzzTest {
    @FuzzTest
    fun fuzzCurrencyFromString(data: FuzzedDataProvider) {
        val code = data.consumeRemainingAsString()
        Currency.fromString(code)
    }

    @FuzzTest
    fun fuzzToNumber(data: FuzzedDataProvider) {
        val input = data.consumeRemainingAsString()
        input.toNumber()
    }
}
