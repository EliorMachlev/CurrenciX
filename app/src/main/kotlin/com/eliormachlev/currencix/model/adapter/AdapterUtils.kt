package com.eliormachlev.currencix.model.adapter

import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.lang.reflect.Type
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.pow

/** `List<Rate>`, as Moshi keys it. */
internal val RATE_LIST_TYPE: Type = Types.newParameterizedType(List::class.java, Rate::class.java)

/** `Map<LocalDate, Rate>`, as Moshi keys it. */
internal val DATED_RATES_TYPE: Type = Types.newParameterizedType(Map::class.java, LocalDate::class.java, Rate::class.java)

/**
 * A [JsonAdapter] that knows the [type] it handles, so [register] can add it
 * to a Moshi builder without the call site repeating the type.
 */
internal abstract class TypedJsonAdapter<T>(
    val type: Type,
) : JsonAdapter<T>()

/** A [TypedJsonAdapter] for a response the app only ever reads. */
internal abstract class ResponseAdapter<T>(
    type: Type,
) : TypedJsonAdapter<T>(type) {
    final override fun toJson(
        writer: JsonWriter,
        value: T?,
    ): Unit = throw UnsupportedOperationException("${javaClass.simpleName} only reads JSON")
}

/** A [Rate] once both of its parts have been read; null while either is missing. */
internal fun rateOrNull(
    currency: Currency?,
    value: BigDecimal?,
): Rate? = if (currency != null && value != null) Rate(currency, value) else null

/** Adds [adapter] for the type it declares. */
internal fun <T> Moshi.Builder.register(adapter: TypedJsonAdapter<T>): Moshi.Builder = add(adapter.type, adapter)

// Guard for adapters whose payload must be a JSON object: if the next value is
// anything else (null, array, string, ...), consume it and return true so the
// caller can bail out with `null`. Consuming matters — Moshi's contract is one
// value per fromJson call, and Retrofit's converter rejects a document with
// input left over ("JSON document was not fully consumed") instead of passing
// the null through to the provider's "empty JSON" error.
internal fun JsonReader.skipIfNotObject(): Boolean {
    if (peek() == JsonReader.Token.BEGIN_OBJECT) return false
    skipValue()
    return true
}

// Both InforEuro endpoints return a JSON array on success and an
// { "message": "..." } object on failure. Peek at the token: on an array,
// walk it via [onArray] (begin/endArray are handled here); otherwise pass
// the error message to [onError].
internal inline fun <T> JsonReader.readArrayOrError(
    onError: (String?) -> T,
    onArray: (JsonReader) -> T,
): T {
    if (peek() != JsonReader.Token.BEGIN_ARRAY) return onError(readErrorMessage())
    beginArray()
    val result = onArray(this)
    endArray()
    return result
}

// Response field names more than one adapter reads.
internal const val FIELD_MESSAGE = "message"
internal const val FIELD_RATES = "rates"
internal const val FIELD_BASE = "base"

// Bank of Canada's Valet API: the rows of a series, each dated by "d".
internal const val BANK_OF_CANADA_OBSERVATIONS = "observations"
internal const val BANK_OF_CANADA_DATE = "d"

// Norges Bank's SDMX-ML: one <Series> per currency, each observation dated
// by its TIME_PERIOD attribute.
internal const val NORGES_BANK_SERIES_TAG = "Series"
internal const val NORGES_BANK_TIME_PERIOD = "TIME_PERIOD"

/**
 * Reads the whole current object (Retrofit rejects a response its adapter
 * left partly unread) and returns the first non-null answer of [onField],
 * which gets each field's name and must consume its value.
 */
internal inline fun <T : Any> JsonReader.readObjectFirstOf(onField: (name: String) -> T?): T? {
    var result: T? = null
    beginObject()
    while (hasNext()) {
        val found = onField(nextName())
        result = result ?: found
    }
    endObject()
    return result
}

/**
 * One currency's value in a Bank of Canada observation: the field [name]
 * (FXUSDCAD, …) holds { "v": "1.3672" }, Canadian dollars per unit.
 */
internal fun JsonReader.readBankOfCanadaValue(name: String): Pair<Currency?, BigDecimal> {
    val currency = Currency.fromString(name.bankOfCanadaIso())
    beginObject()
    skipName() // always "v"
    val value = BigDecimal(nextString())
    endObject()
    return currency to value
}

// Providers surface error payloads as { "message": "..." }. This helper reads
// that message field out of the current object, skipping anything else.
internal fun JsonReader.readErrorMessage(): String? {
    beginObject()
    var message: String? = null
    while (hasNext()) {
        if (nextName() == FIELD_MESSAGE) {
            message = nextString()
        } else {
            skipValue()
        }
    }
    endObject()
    return message
}

// Bank Rossii serializes dates as dd.MM.yyyy in every response.
internal val BANK_ROSSII_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** The current element's Date attribute, as Bank Rossii writes it (dd.MM.yyyy). */
internal fun XmlPullParser.bankRossiiDate(): LocalDate = LocalDate.parse(getAttributeValue(null, "Date"), BANK_ROSSII_DATE_FORMATTER)

// dd/MM/yyyy: InforEuro's timeline dateStart/dateEnd, and Bank Rossii's
// date query parameters.
internal val SLASHED_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy")

// Faroese króna isn't listed by upstream APIs; mirror the Danish krone
// value into the response so FOK still shows up in the UI as a 1:1 peg.
internal fun MutableList<Rate>.addFokFromDkkIfMissing() {
    if (any { it.currency == Currency.FOK }) return
    find { it.currency == Currency.DKK }?.value?.let { dkk ->
        add(Rate(Currency.FOK, dkk))
    }
}

// Namespace-unaware XmlPullParser bound to [inputStream]. The five XML
// parsers all want this exact configuration.
internal fun newXmlPullParser(inputStream: InputStream): XmlPullParser =
    XmlPullParserFactory
        .newInstance()
        .apply { isNamespaceAware = false }
        .newPullParser()
        .apply { setInput(inputStream, null) }

// Norges Bank encodes the currency's decimal scale as UNIT_MULT (an integer
// exponent). Missing/invalid values fall back to 1 (10^0).
internal fun XmlPullParser.norgesBankUnitMultiplier(): Int =
    getAttributeValue(null, "UNIT_MULT")
        ?.toIntOrNull()
        ?.let { 10.0.pow(it).toInt() } ?: 1

// BankOfCanada quotes series names as "FX<iso>CAD" (e.g. "FXUSDCAD"); the
// currency ISO-4217 alpha code lives at indices [2, 5). Shared by the rates
// and timeline adapters so a shape change is edited in one place.
private const val BANK_OF_CANADA_ISO_START: Int = 2
private const val BANK_OF_CANADA_ISO_END: Int = 5

internal fun String.bankOfCanadaIso(): String = substring(BANK_OF_CANADA_ISO_START, BANK_OF_CANADA_ISO_END)

// Error string that every provider/adapter surfaces when a well-formed
// upstream response contained no usable rates. Hoisted so the wording is
// edited in one place.
internal const val NO_DATA_ERROR: String = "No data found."

// Lazily yields every day in [start, endInclusive].
internal fun dateSequence(
    start: LocalDate,
    endInclusive: LocalDate,
): Sequence<LocalDate> =
    generateSequence(start) { current ->
        if (current.isBefore(endInclusive)) current.plusDays(1) else null
    }
