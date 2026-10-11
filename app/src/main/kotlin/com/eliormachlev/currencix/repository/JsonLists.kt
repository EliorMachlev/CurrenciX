package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.util.newId
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

// JSON helpers shared by the stores that keep lists as a JSON array in one
// preference (fees, saved carts) and by their file formats.

/** Each object in this array, skipping entries that aren't objects. */
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull(::optJSONObject)

/** This list as a JSON array of [toJson] objects. */
internal fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject): JSONArray = JSONArray().also { array -> forEach { array.put(toJson(it)) } }

/**
 * A list stored as a JSON array, each object read by [parse] (null skips it).
 * A missing or blank one reads as empty, and so does a malformed one, which
 * is logged under [tag] as a [what] list being dropped.
 */
internal fun <T> parseJsonList(
    json: String?,
    tag: String,
    what: String,
    parse: (JSONObject) -> T?,
): List<T> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        JSONArray(json).objects().mapNotNull(parse)
    } catch (e: JSONException) {
        Timber.tag(tag).w(e, "Malformed %s list, dropping it", what)
        emptyList()
    }
}

/** The object's [key] id, or a new one when it has none (entries saved before ids). */
internal fun JSONObject.optIdOrNew(key: String): String = optString(key, "").ifEmpty { newId() }

/** This list with [item] in place of the first entry that [isSame], or added at the end when none is. */
internal fun <T> List<T>.replacingOrAppending(
    item: T,
    isSame: (T) -> Boolean,
): List<T> = if (any(isSame)) map { if (isSame(it)) item else it } else this + item
