package com.eliormachlev.currencix.model.adapter

import android.util.Xml
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.pow

/** A [Rate] once both of its parts have been read; null while either is missing. */
internal fun rateOrNull(
    currency: Currency?,
    value: BigDecimal?,
): Rate? = if (currency != null && value != null) Rate(currency, value) else null

/**
 * A string's or a number's text, a number exactly as written.
 * @throws IllegalArgumentException for anything else (an object, an array, `null`).
 */
internal fun JsonElement.text(): String {
    require(this is JsonPrimitive && this !is JsonNull) { "Expected a string or a number, not $this" }
    return content
}

/** The number this string or number is, exactly as written. */
internal fun JsonElement.decimal(): BigDecimal = BigDecimal(text())

// Bank of Canada's Valet API: the rows of a series, each dated by "d".
internal const val BANK_OF_CANADA_OBSERVATIONS = "observations"
internal const val BANK_OF_CANADA_DATE = "d"
private const val BANK_OF_CANADA_VALUE = "v"

// Norges Bank's SDMX-ML: one <Series> per currency, each observation dated
// by its TIME_PERIOD attribute.
internal const val NORGES_BANK_SERIES_TAG = "Series"
internal const val NORGES_BANK_TIME_PERIOD = "TIME_PERIOD"

// Norges Bank's observation element and the attributes the parsers read off it and its series.
internal const val NORGES_BANK_OBSERVATION_TAG = "Obs"
internal const val NORGES_BANK_BASE_CURRENCY = "BASE_CUR"
internal const val NORGES_BANK_VALUE = "OBS_VALUE"
private const val NORGES_BANK_UNIT_MULTIPLIER = "UNIT_MULT"

// Bank Rossii's rate element: roubles per one unit of the currency, a comma as the decimal point.
internal const val BANK_ROSSII_RATE_TAG = "VunitRate"
private const val BANK_ROSSII_DATE = "Date"

// UNIT_MULT is a power of ten.
private const val DECIMAL_BASE = 10.0

/**
 * One currency's value in a Bank of Canada observation: the field [name]
 * (FXUSDCAD, …) holds { "v": "1.3672" }, Canadian dollars per unit.
 */
internal fun JsonElement.bankOfCanadaValue(name: String): Pair<Currency?, BigDecimal> {
    val currency = Currency.fromString(name.bankOfCanadaIso())
    val value = requireNotNull(jsonObject[BANK_OF_CANADA_VALUE]) { "No value for $name" }.decimal()
    return currency to value
}

// Bank Rossii serializes dates as dd.MM.yyyy in every response.
internal val BANK_ROSSII_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** The current element's Date attribute, as Bank Rossii writes it (dd.MM.yyyy). */
internal fun XmlPullParser.bankRossiiDate(): LocalDate =
    LocalDate.parse(getAttributeValue(null, BANK_ROSSII_DATE), BANK_ROSSII_DATE_FORMATTER)

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
// parsers all want this exact configuration. Xml.newPullParser() builds the
// platform parser directly (XmlPullParserFactory loads it by class name), but
// turns namespaces and DOCTYPE processing on, so both are set back off: the
// feeds need neither, and a DOCTYPE from the network stays unprocessed.
internal fun newXmlPullParser(inputStream: InputStream): XmlPullParser =
    Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        setInput(inputStream, null)
    }

// Norges Bank encodes the currency's decimal scale as UNIT_MULT (an integer
// exponent). Missing/invalid values fall back to 1 (10^0).
internal fun XmlPullParser.norgesBankUnitMultiplier(): Int =
    getAttributeValue(null, NORGES_BANK_UNIT_MULTIPLIER)
        ?.toIntOrNull()
        ?.let { DECIMAL_BASE.pow(it).toInt() } ?: 1

/** The series' base currency (Norges Bank's BASE_CUR), when the app knows it. */
internal fun XmlPullParser.norgesBankBaseCurrency(): Currency? = Currency.fromString(getAttributeValue(null, NORGES_BANK_BASE_CURRENCY))

/** The observation's value and date (Norges Bank's OBS_VALUE and TIME_PERIOD). */
internal fun XmlPullParser.norgesBankObservation(): Pair<LocalDate, BigDecimal?> =
    LocalDate.parse(getAttributeValue(null, NORGES_BANK_TIME_PERIOD)) to getAttributeValue(null, NORGES_BANK_VALUE).toBigDecimalOrNull()

/** Bank Rossii's rate text ("12,3456" roubles for one unit) as the currency per rouble. */
internal fun bankRossiiRate(text: String): BigDecimal = BigDecimal.ONE.divide(text.replace(',', '.').toBigDecimal(), MathContext.DECIMAL128)

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
