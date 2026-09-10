package com.eliormachlev.currencix.screenshots

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerConversion
import com.eliormachlev.currencix.view.main.spinner.SearchableCurrencyPicker
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

// Currency picker rendered with a small representative rate list and a
// couple of starred entries so the "favorites" section is populated. Two
// tests: the default view (search field empty, favorites + all rates) and
// the "starred filter on" state that hides non-favorites.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class SearchableCurrencyPickerScreenshotTest {
    @Test fun currencyPickerAll() =
        captureMatrix("currency_picker_all") {
            SearchableCurrencyPicker(
                rates = SAMPLE_RATES,
                stars = SAMPLE_STARS,
                filterStarred = false,
                conversion = SAMPLE_CONVERSION,
                disabledCurrency = null,
                onRateClicked = {},
                onStarClicked = {},
                onToggleStarredFilter = {},
                onStarredOrderChanged = {},
            )
        }

    @Test fun currencyPickerStarredOnly() =
        captureMatrix("currency_picker_starred") {
            SearchableCurrencyPicker(
                rates = SAMPLE_RATES,
                stars = SAMPLE_STARS,
                filterStarred = true,
                conversion = SAMPLE_CONVERSION,
                disabledCurrency = null,
                onRateClicked = {},
                onStarClicked = {},
                onToggleStarredFilter = {},
                onStarredOrderChanged = {},
            )
        }

    companion object {
        private val SAMPLE_RATES =
            listOf(
                Rate(Currency.USD, BigDecimal("1.00")),
                Rate(Currency.EUR, BigDecimal("0.92")),
                Rate(Currency.GBP, BigDecimal("0.79")),
                Rate(Currency.JPY, BigDecimal("149.30")),
                Rate(Currency.CAD, BigDecimal("1.36")),
                Rate(Currency.AUD, BigDecimal("1.52")),
                Rate(Currency.CHF, BigDecimal("0.88")),
                Rate(Currency.CNY, BigDecimal("7.24")),
            )

        private val SAMPLE_STARS = listOf(Currency.EUR, Currency.GBP)

        private val SAMPLE_CONVERSION =
            CurrencyPickerConversion(
                baseRate = Rate(Currency.USD, BigDecimal("1.00")),
                baseSum = BigDecimal("100"),
            )
    }
}
