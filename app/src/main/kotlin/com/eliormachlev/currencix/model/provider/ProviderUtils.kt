package com.eliormachlev.currencix.model.provider

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.adapter.LocalDateAdapter
import com.eliormachlev.currencix.util.HttpClientProvider
import com.eliormachlev.currencix.util.fetch
import java.io.IOException
import java.time.format.DateTimeFormatter

// FOK (Faroese króna) is pegged 1:1 to DKK and not listed by upstream APIs —
// query DKK instead and let callers relabel the result on the way back.
internal fun Currency.apiCodeOrDkkForFok(): String = if (this == Currency.FOK) "DKK" else this.iso4217Alpha()

// One shared KotlinJsonAdapterFactory across every provider. The factory
// keeps its own class-to-adapter cache internally, so a single instance
// means the reflection scan runs once app-wide per data class instead of
// once per HTTP request. Safe to share: the factory is stateless.
internal val SHARED_KOTLIN_JSON_ADAPTER_FACTORY: KotlinJsonAdapterFactory =
    KotlinJsonAdapterFactory()

// Stateless date adapter — hoisted for the same reason as the factory above.
internal val SHARED_LOCAL_DATE_ADAPTER: LocalDateAdapter = LocalDateAdapter()

// Timeline lookback window shared by the providers that don't guarantee a
// same-day quote (BankOfCanada, NorgesBank). Widening the window here picks up
// the most recent business-day rate when the requested date lands on a weekend
// or holiday — 7 days safely covers long weekends.
internal const val TIMELINE_LOOKBACK_DAYS: Long = 7L

// ISO-8601 (yyyy-MM-dd) — the format every upstream in this app accepts for
// query-string dates. Hoisted so providers don't each keep a local alias.
internal val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

// Build a Moshi instance preconfigured with the shared Kotlin adapter factory.
// Callers add their per-request adapters inside [block].
internal inline fun moshi(block: Moshi.Builder.() -> Unit = {}): Moshi =
    Moshi
        .Builder()
        .apply(block)
        .addLast(SHARED_KOTLIN_JSON_ADAPTER_FACTORY)
        .build()

// GETs [url] as JSON and parses it via [adapter]. Empty top-level JSON turns
// into an IOException tagged with [providerLabel] so the caller's error
// surface can identify which provider failed to decode.
internal suspend fun <T : Any> fetchJson(
    context: Context?,
    url: String,
    providerLabel: String,
    adapter: JsonAdapter<T>,
): Result<T> =
    HttpClientProvider.fetch(context, url) { body ->
        adapter.fromJson(body.source()) ?: throw IOException("$providerLabel: empty JSON")
    }
