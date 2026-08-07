package com.eliormachlev.currencix.repository

import android.content.Context
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import java.time.LocalDate

object ExchangeRatesService {
    /**
     * Get all the current exchange rates from the given api provider. Base will be Euro.
     */
    suspend fun getRates(
        apiProvider: ApiProvider,
        date: LocalDate? = null,
        context: Context? = null,
    ): Result<ExchangeRates> = apiProvider.getRates(context, date)

    /**
     * Get the historic rates between the given base and symbol for [startDate]..[endDate]
     * (defaults to the past year). Won't get all the symbols, as it makes a big difference
     * in transferred data size: ~12 KB for one symbol to ~840 KB for all symbols.
     */
    suspend fun getTimeline(
        apiProvider: ApiProvider,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate = LocalDate.now().minusYears(1),
        endDate: LocalDate = LocalDate.now(),
        context: Context? = null,
    ): Result<Timeline> = apiProvider.getTimeline(context, base, symbol, startDate, endDate)
}
