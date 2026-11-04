package com.eliormachlev.currencix.repository.json

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonObject as JsonElementObject

// The app's own JSON (saved lists, backups, cart files, cached rates) read and
// written with kotlinx.serialization's JSON tree: no reflection and no
// generated serializers, just maps, lists, strings, numbers and booleans.
// Fields are read leniently, as they always have been: a missing or mistyped
// field reads as its fallback, so a hand-edited file still opens.

private const val MALFORMED_JSON = "Malformed JSON"

// Strict JSON (no comments, no unquoted strings), indented for files people may open.
private val PRETTY_JSON = Json { prettyPrint = true }

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

/**
 * [text] as one JSON document, numbers kept exactly as they were written.
 * @throws MalformedJsonException when it isn't well-formed JSON.
 */
internal fun parseJsonElement(text: String): JsonElement =
    try {
        Json.parseToJsonElement(text)
    } catch (e: SerializationException) {
        throw MalformedJsonException(e.message ?: MALFORMED_JSON, e)
    }

/**
 * [text] as maps, lists, strings, doubles and booleans. An object naming a
 * field twice is malformed here: which of the two would count is a guess.
 * @throws MalformedJsonException when it isn't well-formed JSON.
 */
private fun parseJsonValue(text: String): Any? {
    val element = parseJsonElement(text)
    duplicateFieldName(text)?.let { throw MalformedJsonException("The field \"$it\" is there twice") }
    return element.toValue()
}

private fun JsonElement.toValue(): Any? =
    when (this) {
        is JsonElementObject -> mapValuesTo(LinkedHashMap()) { (_, value) -> value.toValue() }
        is JsonArray -> map { it.toValue() }
        JsonNull -> null
        is JsonPrimitive -> if (isString) content else booleanOrNull ?: content.toDouble()
    }

/**
 * [value] (maps with string keys, lists, strings, numbers, booleans) as JSON
 * text, indented when [pretty]. Nulls in maps are left out.
 */
internal fun jsonText(
    value: Any?,
    pretty: Boolean = false,
): String = (if (pretty) PRETTY_JSON else Json).encodeToString(JsonElement.serializer(), value.toElement())

private fun Any?.toElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this.also { require(it.toDouble().isFinite()) { "Not a finite number: $it" } })
        is Map<*, *> -> toObjectElement()
        is Collection<*> -> JsonArray(map { it.toElement() })
        else -> throw IllegalArgumentException("Not a JSON value: ${this::class.java.simpleName}")
    }

// The map's fields in order, with nulls left out.
private fun Map<*, *>.toObjectElement(): JsonElementObject =
    JsonElementObject(
        entries.filter { it.value != null }.associateTo(LinkedHashMap()) { (key, value) ->
            require(key is String) { "A JSON field name must be a string: $key" }
            key to value.toElement()
        },
    )

/** A JSON object's fields, in order, with nulls left out (as a field that isn't there). */
internal inline fun buildJsonObject(build: MutableMap<String, Any>.() -> Unit): Map<String, Any> = LinkedHashMap<String, Any>().apply(build)

// A whole number without the ".0" (as org.json wrote it), so "42" reads back as "42".
private fun numberText(value: Double): String {
    val whole = value.toLong()
    return if (whole.toDouble() == value) whole.toString() else value.toString()
}
