package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.adapter.OpenExchangeratesRatesAdapter
import com.eliormachlev.currencix.model.adapter.register
import com.eliormachlev.currencix.model.provider.api.OpenExchangeratesApi
import com.eliormachlev.currencix.util.ApiHttpError
import com.squareup.moshi.Moshi
import java.time.LocalDate

private const val HTTP_UNAUTHORIZED = 401

// Compact JSON, and no unofficial / black-market rates — the fixed query
// flags every request carries.
private const val PRETTY_PRINT = false
private const val SHOW_ALTERNATIVE = false

// The rates adapter is stateless, so one Moshi serves every request.
private val RATES_MOSHI: Moshi = moshi { register(OpenExchangeratesRatesAdapter()) }

class OpenExchangerates : ApiProvider.Api {
    override val name = "Open Exchangerates"
    override val nameRes = R.string.api_openExchangeRates_name

    override fun descriptionShort(context: Context) = context.getText(R.string.api_openExchangeRates_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_openExchangeRates_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_openExchangeRates_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = context.getText(R.string.api_openExchangeRates_hint)

    override val baseUrl = "https://openexchangerates.org/api"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
        secrets: ApiSecrets,
    ): Result<ExchangeRates> {
        val apiKey = secrets.openExchangeRatesApiKey
        if (apiKey.isNullOrBlank()) {
            return Result.failure(Exception(context?.getString(R.string.error_no_api_key)))
        }

        val api = retrofitApi<OpenExchangeratesApi>(context, RATES_MOSHI)
        val result =
            fetchRetrofit {
                if (date != null) {
                    api.getHistorical(date.format(ISO_DATE), apiKey, PRETTY_PRINT, SHOW_ALTERNATIVE)
                } else {
                    api.getLatest(apiKey, PRETTY_PRINT, SHOW_ALTERNATIVE)
                }
            }.map { it.copy(provider = ApiProvider.OPEN_EXCHANGERATES) }

        val err = result.exceptionOrNull()
        return if (err is ApiHttpError && err.statusCode == HTTP_UNAUTHORIZED) {
            Result.failure(Exception(context?.getString(R.string.error_invalid_api_key)))
        } else {
            result
        }
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<Timeline> = Result.failure(Exception(context?.getString(R.string.error_unsupported_timeline)))
}
