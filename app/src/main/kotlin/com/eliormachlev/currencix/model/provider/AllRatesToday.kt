package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.model.adapter.AllRatesTodayRatesAdapter
import com.eliormachlev.currencix.model.adapter.AllRatesTodayTimelineAdapter
import com.eliormachlev.currencix.model.adapter.register
import com.eliormachlev.currencix.model.provider.api.AllRatesTodayApi
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Rates are asked for against the euro, like the other providers that let
// the app choose: conversions go through it, and it keeps rounding small.
private val BASE = Currency.EUR

// A past day's rates are its rates at noon UTC: well inside the day
// wherever the user is.
private val HISTORICAL_TIME_OF_DAY: LocalTime = LocalTime.NOON

/**
 * AllRatesToday ([allratestoday.com](https://allratestoday.com)): mid-market
 * rates for ~160 currencies, updated about every minute, against the user's
 * own API key (sent as a bearer token). The free plan is 300 requests in
 * all, so its description says so and the refresh interval is the user's.
 */
class AllRatesToday : ApiProvider.Api {
    override val name = "AllRatesToday"
    override val nameRes = R.string.api_allRatesToday_name

    override fun descriptionShort(context: Context) = context.getText(R.string.api_allRatesToday_descriptionShort)

    override fun getDescriptionLong(context: Context) = context.getText(R.string.api_allRatesToday_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) = context.getText(R.string.api_allRatesToday_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) = context.getText(R.string.api_allRatesToday_hint)

    override val baseUrl = "https://allratestoday.com"

    override suspend fun getRates(
        context: Context?,
        date: LocalDate?,
        secrets: ApiSecrets,
    ): Result<ExchangeRates> {
        val apiKey = secrets.allRatesTodayApiKey
        if (apiKey.isNullOrBlank()) return missingApiKey(context)
        val api = retrofitApi<AllRatesTodayApi>(context, moshi { register(AllRatesTodayRatesAdapter(BASE)) })
        val time = date?.atTime(HISTORICAL_TIME_OF_DAY)?.atOffset(ZoneOffset.UTC)?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        return fetchRetrofit { api.getRates(bearer(apiKey), source = BASE.iso4217Alpha(), time = time) }
            .explainKeyErrors(context)
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
        secrets: ApiSecrets,
    ): Result<Timeline> {
        val apiKey = secrets.allRatesTodayApiKey
        if (apiKey.isNullOrBlank()) return missingApiKey(context)
        val api = retrofitApi<AllRatesTodayApi>(context, moshi { register(AllRatesTodayTimelineAdapter(base, symbol)) })
        return fetchRetrofit {
            api.getTimeline(
                bearer(apiKey),
                source = base.apiCodeOrDkkForFok(),
                target = symbol.apiCodeOrDkkForFok(),
                from = startDate.format(ISO_DATE),
                // `to` is exclusive: the day after, so the end date is in the series.
                toExclusive = endDate.plusDays(1).format(ISO_DATE),
            )
        }.explainKeyErrors(context)
    }

    private fun bearer(apiKey: String) = "Bearer $apiKey"
}
