package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.adapter.ResponseAdapter
import com.eliormachlev.currencix.util.ApiHttpError
import com.eliormachlev.currencix.util.HttpClientProvider
import com.eliormachlev.currencix.util.RetrofitProvider
import com.eliormachlev.currencix.util.fetch
import com.eliormachlev.currencix.util.retrofitCall
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.time.format.DateTimeFormatter

// FOK (Faroese króna) is pegged 1:1 to DKK and not listed by upstream APIs —
// query DKK instead and let callers relabel the result on the way back.
internal fun Currency.apiCodeOrDkkForFok(): String = if (this == Currency.FOK) Currency.DKK.iso4217Alpha() else this.iso4217Alpha()

// Timeline lookback window shared by the providers that don't guarantee a
// same-day quote (BankOfCanada, NorgesBank). Widening the window here picks up
// the most recent business-day rate when the requested date lands on a weekend
// or holiday — 7 days safely covers long weekends.
internal const val TIMELINE_LOOKBACK_DAYS: Long = 7L

// Path segment for "today's rates" on Frankfurter-style APIs (versus an
// ISO-8601 date), used by Frankfurter.app.
internal const val LATEST_DATE_PATH = "latest"

// ISO-8601 (yyyy-MM-dd) — the format every upstream in this app accepts for
// query-string dates. Hoisted so providers don't each keep a local alias.
internal val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

// Chains a step that can itself fail: [next] runs on success, a failure passes through.
internal inline fun <T, R> Result<T>.flatMap(next: (T) -> Result<R>): Result<R> = fold(onSuccess = next, onFailure = { Result.failure(it) })

private fun emptyJson(providerLabel: String) = IOException("$providerLabel: empty JSON")

// Binds the Retrofit interface [T] to this provider's [ApiProvider.Api.baseUrl],
// on top of the shared OkHttp client.
internal inline fun <reified T : Any> ApiProvider.Api.retrofitApi(context: Context?): T =
    RetrofitProvider.retrofit(context, baseUrl).create(T::class.java)

// Runs a Retrofit [call] and reads its body with [adapter]: non-2xx becomes
// [ApiHttpError]; no body, or one the adapter finds nothing in (JSON `null`),
// an IOException labelled with the provider name; malformed JSON, the
// parser's SerializationException. Interfaces return [Response] rather than
// the bare body because Retrofit's suspend adapter would otherwise throw an
// opaque KotlinNullPointerException on a null body.
internal suspend fun <T : Any> ApiProvider.Api.fetchRetrofit(
    adapter: ResponseAdapter<T>,
    call: suspend () -> Response<ResponseBody>,
): Result<T> =
    retrofitCall {
        val response = call()
        if (!response.isSuccessful) throw ApiHttpError(response.code())
        val body = response.body() ?: throw emptyJson(name)
        val json = body.use { Json.parseToJsonElement(it.string()) }
        adapter.decode(json) ?: throw emptyJson(name)
    }

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * A keyed provider's failure in words the user can act on: the key is
 * wrong (401), the plan doesn't include this (403), or the request quota is
 * used up (429). Anything else passes through unchanged.
 */
internal fun <T> Result<T>.explainKeyErrors(context: Context?): Result<T> {
    val status = (exceptionOrNull() as? ApiHttpError)?.statusCode ?: return this
    val message =
        when (status) {
            HTTP_UNAUTHORIZED -> R.string.error_invalid_api_key
            HTTP_FORBIDDEN -> R.string.error_api_plan_required
            HTTP_TOO_MANY_REQUESTS -> R.string.error_api_quota_exceeded
            else -> return this
        }
    return Result.failure(Exception(context?.getString(message)))
}

/** The "no key yet" failure a keyed provider answers with before any request. */
internal fun <T> missingApiKey(context: Context?): Result<T> = Result.failure(Exception(context?.getString(R.string.error_no_api_key)))
