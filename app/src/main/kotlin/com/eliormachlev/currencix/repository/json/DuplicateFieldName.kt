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
internal fun duplicateFieldName(json: String): String? = FieldNameScanner(json).firstDuplicate()

private class FieldNameScanner(
    private val json: String,
) {
    // The names seen in each object around the current position; null for an array.
    private val open = ArrayDeque<MutableSet<String>?>()
    private var expectName = false

    fun firstDuplicate(): String? {
        var i = 0
        while (i < json.length) {
            val (next, repeated) = step(i)
            if (repeated != null) return repeated
            i = next
        }
        return null
    }

    // Reads the character at [i]: where reading goes on, and the name it
    // repeats when it opens a field name already given in its object.
    private fun step(i: Int): Pair<Int, String?> {
        when (json[i]) {
            '{' -> enter(HashSet())
            '[' -> enter(null)
            '}', ']' -> leave()
            ',' -> expectName = open.lastOrNull() != null
            QUOTE -> return string(i)
        }
        return i + 1 to null
    }

    private fun enter(names: MutableSet<String>?) {
        open.addLast(names)
        expectName = names != null
    }

    private fun leave() {
        open.removeLast()
        expectName = false
    }

    private fun string(start: Int): Pair<Int, String?> {
        val end = stringEnd(json, start)
        val repeated =
            if (expectName) {
                expectName = false
                stringValue(json, start, end).takeIf { open.lastOrNull()?.add(it) == false }
            } else {
                null
            }
        return end + 1 to repeated
    }
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
