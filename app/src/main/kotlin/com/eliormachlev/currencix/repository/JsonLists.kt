package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.repository.json.JsonObject
import com.eliormachlev.currencix.repository.json.MalformedJsonException
import com.eliormachlev.currencix.repository.json.jsonText
import com.eliormachlev.currencix.repository.json.objects
import com.eliormachlev.currencix.repository.json.parseJsonArray
import timber.log.Timber

// JSON helpers for lists kept as a JSON array in one preference (the rate
// alerts), and for the stores in general.

/** This list as JSON array text, each entry written as the object [toJson] gives. */
internal fun <T> List<T>.toJsonArrayText(toJson: (T) -> Map<String, Any>): String = jsonText(map(toJson))

/**
 * A list stored as a JSON array, each object read by [parse] (null skips it).
 * A missing or blank one reads as empty, and so does a malformed one, which
 * is logged under [tag] as a [what] list being dropped.
 */
internal fun <T> parseJsonList(
    json: String?,
    tag: String,
    what: String,
    parse: (JsonObject) -> T?,
): List<T> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        parseJsonArray(json).objects().mapNotNull(parse)
    } catch (e: MalformedJsonException) {
        Timber.tag(tag).w(e, "Malformed %s list, dropping it", what)
        emptyList()
    }
}

/** The object's [key] id, or a new one when it has none (entries saved before ids). */
internal fun JsonObject.optIdOrNew(key: String): String = idOrNew(optString(key))

/** This list with [item] in place of the first entry that [isSame], or added at the end when none is. */
internal fun <T> List<T>.replacingOrAppending(
    item: T,
    isSame: (T) -> Boolean,
): List<T> = if (any(isSame)) map { if (isSame(it)) item else it } else this + item
