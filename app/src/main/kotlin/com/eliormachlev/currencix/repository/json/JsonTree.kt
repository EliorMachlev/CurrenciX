package com.eliormachlev.currencix.repository.json

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import okio.Buffer
import okio.BufferedSource
import java.io.IOException

// The app's own JSON (saved lists, backups, cart files, cached rates) read and
// written with Moshi's streaming reader and writer: no reflection and no
// adapters, just maps, lists, strings, numbers and booleans. Fields are read
// leniently, as they always have been: a missing or mistyped field reads as
// its fallback, so a hand-edited or older file still opens.

private const val PRETTY_INDENT = "  "

/** Text that isn't the JSON it should be: not JSON at all, or the wrong shape. */
internal class MalformedJsonException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** One JSON object, read field by field. */
@JvmInline
internal value class JsonObject(
    private val fields: Map<*, *>,
) {
    val keys: List<String> get() = fields.keys.filterIsInstance<String>()

    fun has(key: String): Boolean = key in fields

    /** The field as text: a string as it is, a number or boolean written out, else [fallback]. */
    fun optString(
        key: String,
        fallback: String = "",
    ): String =
        when (val value = fields[key]) {
            is String -> value
            is Number -> numberText(value.toDouble())
            is Boolean -> value.toString()
            else -> fallback
        }

    /** The field's text, or null when it's missing or empty. */
    fun optStringOrNull(key: String): String? = optString(key).ifEmpty { null }

    fun optDouble(
        key: String,
        fallback: Double = Double.NaN,
    ): Double =
        when (val value = fields[key]) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: fallback
            else -> fallback
        }

    /** The field as a whole number (a fraction is cut off, as org.json did), or [fallback]. */
    fun optInt(
        key: String,
        fallback: Int = 0,
    ): Int = optDouble(key).takeIf(Double::isFinite)?.toInt() ?: fallback

    fun optLong(
        key: String,
        fallback: Long = 0L,
    ): Long = optDouble(key).takeIf(Double::isFinite)?.toLong() ?: fallback

    fun optBoolean(
        key: String,
        fallback: Boolean = false,
    ): Boolean =
        when (val value = fields[key]) {
            is Boolean -> value
            is String -> value.lowercase().toBooleanStrictOrNull() ?: fallback
            else -> fallback
        }

    fun optObject(key: String): JsonObject? = (fields[key] as? Map<*, *>)?.let(::JsonObject)

    fun optArray(key: String): List<*>? = fields[key] as? List<*>

    /** The objects in the field's array, skipping entries that aren't objects. */
    fun optObjects(key: String): List<JsonObject> = optArray(key)?.objects().orEmpty()
}

/** Each object in this array, skipping entries that aren't objects. */
internal fun List<*>.objects(): List<JsonObject> = filterIsInstance<Map<*, *>>().map(::JsonObject)

/** [text] as a JSON object. @throws MalformedJsonException when it isn't one. */
internal fun parseJsonObject(text: String): JsonObject =
    (parseJsonValue(text) as? Map<*, *>)?.let(::JsonObject) ?: throw MalformedJsonException("Not a JSON object")

/** [text] as a JSON array. @throws MalformedJsonException when it isn't one. */
internal fun parseJsonArray(text: String): List<*> = parseJsonValue(text) as? List<*> ?: throw MalformedJsonException("Not a JSON array")

// The whole document as maps, lists, strings, doubles and booleans.
private fun parseJsonValue(text: String): Any? = readJson(Buffer().writeUtf8(text)) { it.readJsonValue() }

/**
 * Reads one JSON document from [source] with [read], and checks nothing
 * follows it. @throws MalformedJsonException when it isn't well-formed JSON.
 */
internal fun <T> readJson(
    source: BufferedSource,
    read: (JsonReader) -> T,
): T =
    try {
        JsonReader.of(source).use { reader ->
            read(reader).also {
                if (reader.peek() != JsonReader.Token.END_DOCUMENT) throw MalformedJsonException("Text after the JSON")
            }
        }
    } catch (e: IOException) {
        throw MalformedJsonException(e.message ?: "Malformed JSON", e)
    } catch (e: JsonDataException) {
        throw MalformedJsonException(e.message ?: "Malformed JSON", e)
    }

/**
 * [value] (maps with string keys, lists, strings, numbers, booleans) as JSON
 * text, indented when [pretty]. Nulls in maps are left out.
 */
internal fun jsonText(
    value: Any?,
    pretty: Boolean = false,
): String = writeJson(pretty) { it.jsonValue(value) }

/** The JSON that [write] writes, as text, indented when [pretty]. */
internal fun writeJson(
    pretty: Boolean = false,
    write: (JsonWriter) -> Unit,
): String {
    val buffer = Buffer()
    JsonWriter.of(buffer).use { writer ->
        if (pretty) writer.indent = PRETTY_INDENT
        write(writer)
    }
    return buffer.readUtf8()
}

/** A JSON object's fields, in order, with nulls left out (as a field that isn't there). */
internal inline fun buildJsonObject(build: MutableMap<String, Any>.() -> Unit): Map<String, Any> = LinkedHashMap<String, Any>().apply(build)

// A whole number without the ".0" (as org.json wrote it), so "42" reads back as "42".
private fun numberText(value: Double): String {
    val whole = value.toLong()
    return if (whole.toDouble() == value) whole.toString() else value.toString()
}
