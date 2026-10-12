package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import java.time.LocalDate

/** ECB reference rates over the same API as [FrankfurterApp]; often down ([ApiProvider.FER_EE]). */
class FerEe : ApiProvider.Api {
    override val name = "Fer.ee"
    override val nameRes = R.string.api_ferEe_name

    override fun descriptionShort(context: Context) = context.getText(R.string.api_ferEe_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_ferEe_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_ferEe_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = null

    override val baseUrl = "https://api.fer.ee"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
        secrets: ApiSecrets,
    ): Result<ExchangeRates> = frankfurterRates(context, date, ApiProvider.FER_EE)

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Timeline> = frankfurterTimeline(context, base, symbol, startDate..endDate, ApiProvider.FER_EE)
}
