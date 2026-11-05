package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.provider.api.InforEuroFailure
import com.eliormachlev.currencix.model.provider.api.InforEuroMonth
import com.eliormachlev.currencix.model.provider.api.InforEuroMonthRates
import java.time.LocalDate

/** InforEuro's month of rates as the app's, dated [date]; its message when it has none. */
internal fun InforEuroMonth.toExchangeRates(date: LocalDate): ExchangeRates =
    when (this) {
        is InforEuroMonthRates -> {
            val known = rates.mapNotNull { rateOrNull(it.isoA3Code?.let(Currency::fromString), it.value) }
            ExchangeRates(
                success = known.isNotEmpty(),
                error = null,
                base = Currency.EUR,
                date = date,
                rates = known,
                provider = ApiProvider.INFOR_EURO,
            )
        }

        is InforEuroFailure -> {
            ExchangeRates(
                success = false,
                error = message,
                base = Currency.EUR,
                date = date,
                rates = null,
                provider = ApiProvider.INFOR_EURO,
            )
        }
    }
