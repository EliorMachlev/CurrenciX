package com.eliormachlev.currencix.repository.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

private const val QUOTE = '"'
private const val ESCAPE = '\\'

/**
 * The first field name that one object in [json] gives twice, or null. The
 * JSON tree keeps only the last of the two, so this reads the text itself;
 * [json] must already be well-formed (it isn't checked again here).
 */
internal fun duplicateFieldName(json: String): String? {
    // The names seen in each object around the current position; null for an array.
    val open = ArrayDeque<MutableSet<String>?>()
    var expectName = false
    var i = 0
    while (i < json.length) {
        when (json[i]) {
            '{' -> {
                open.addLast(HashSet())
                expectName = true
            }

            '[' -> {
                open.addLast(null)
                expectName = false
            }

            '}', ']' -> {
                open.removeLast()
                expectName = false
            }

            ',' -> {
                expectName = open.lastOrNull() != null
            }

            QUOTE -> {
                val end = stringEnd(json, i)
                if (expectName) {
                    val name = stringValue(json, i, end)
                    if (open.lastOrNull()?.add(name) == false) return name
                    expectName = false
                }
                i = end
            }
        }
        i++
    }
    return null
}

// The index of the quote closing the string that opens at [start].
private fun stringEnd(
    json: String,
    start: Int,
): Int {
    var i = start + 1
    while (json[i] != QUOTE) i += if (json[i] == ESCAPE) 2 else 1
    return i
}

// The string between the quotes at [start] and [end], its escapes decoded.
private fun stringValue(
    json: String,
    start: Int,
    end: Int,
): String {
    val raw = json.substring(start + 1, end)
    return if (ESCAPE in raw) Json.parseToJsonElement(json.substring(start, end + 1)).jsonPrimitive.content else raw
}
