package de.salomax.currencies.model.provider

import android.content.Context
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.model.adapter.OpenExchangeratesRatesAdapter
import de.salomax.currencies.repository.Database
import de.salomax.currencies.util.ApiHttpError
import java.time.LocalDate

private const val HTTP_UNAUTHORIZED = 401

class OpenExchangerates : ApiProvider.Api() {
    override val name = "Open Exchangerates"

    override fun descriptionShort(context: Context) = context.getText(R.string.api_openExchangeRates_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_openExchangeRates_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_openExchangeRates_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = context.getText(R.string.api_openExchangeRates_hint)

    override val baseUrl = "https://openexchangerates.org/api"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
    ): Result<ExchangeRates> {
        val apiKey = context?.let { Database(it).getOpenExchangeRatesApiKey() }
        if (apiKey.isNullOrBlank()) {
            return Result.failure(Exception(context?.getString(R.string.error_no_api_key)))
        }

        val endpoint =
            if (date != null) {
                "/historical/${date.format(ISO_DATE)}.json"
            } else {
                "/latest.json"
            }
        val adapter =
            moshi { add(OpenExchangeratesRatesAdapter()) }
                .adapter(ExchangeRates::class.java)

        val result =
            fetchJson(
                context,
                "$baseUrl$endpoint?app_id=$apiKey&prettyprint=false&show_alternative=false",
                name,
                adapter,
            ).map { it.copy(provider = ApiProvider.OPEN_EXCHANGERATES) }

        val err = result.exceptionOrNull()
        return if (err is ApiHttpError && err.statusCode == HTTP_UNAUTHORIZED) {
            Result.failure(Exception(context.getString(R.string.error_invalid_api_key)))
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
