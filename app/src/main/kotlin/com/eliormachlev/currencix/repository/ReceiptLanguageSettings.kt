package com.eliormachlev.currencix.repository

import android.content.Context
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.mapped
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.repository.persistence.stringSetPref

private const val KEY_ALLOWED = "_receiptLanguagesAllowed"
private const val KEY_DECLINED = "_receiptLanguagesDeclined"
private const val KEY_KEEP_UNTIL = "_receiptLanguagesKeepUntil"

// "kor=1767225600000": a language's code and when it goes, in epoch milliseconds.
private const val KEEP_UNTIL_SEPARATOR = '='

/**
 * The receipt-reading languages and the user's say on each (TessLanguage
 * codes): which they agreed to download, which they said no to (a language
 * in neither is asked about first, and only one agreed to is downloaded),
 * and when one kept for a trip is deleted again.
 */
class ReceiptLanguageSettings(
    context: Context,
) {
    private val store: PrefStore = PersistenceKey.APP.prefStore(context)

    val allowed: Pref<Set<String>> = store.stringSetPref(KEY_ALLOWED)
    val declined: Pref<Set<String>> = store.stringSetPref(KEY_DECLINED)

    /** When each language kept for a while is deleted, in epoch milliseconds; one not here is kept. */
    val keepUntil: Pref<Map<String, Long>> = store.stringSetPref(KEY_KEEP_UNTIL).mapped(::decodeKeepUntil, ::encodeKeepUntil)

    /** The user's answer about downloading [code]: true yes, false no, null not asked. */
    fun answerFor(code: String): Boolean? =
        when (code) {
            in allowed.get() -> true
            in declined.get() -> false
            else -> null
        }

    /** Records the user's answer about downloading [code]'s data (one set or the other, never both). */
    fun setAllowed(
        code: String,
        allowed: Boolean,
    ) {
        this.allowed.update { if (allowed) it + code else it - code }
        declined.update { if (allowed) it - code else it + code }
    }

    /** Forgets the user's answer about [code], as if never asked. */
    fun forget(code: String) {
        allowed.update { it - code }
        declined.update { it - code }
    }

    /** Keeps [code] until [epochMillis], or for good (null). */
    fun setKeepUntil(
        code: String,
        epochMillis: Long?,
    ) = keepUntil.update { if (epochMillis == null) it - code else it + (code to epochMillis) }
}

internal fun decodeKeepUntil(entries: Set<String>): Map<String, Long> =
    entries
        .mapNotNull { entry ->
            val code = entry.substringBefore(KEEP_UNTIL_SEPARATOR, missingDelimiterValue = "")
            val millis = entry.substringAfter(KEEP_UNTIL_SEPARATOR).toLongOrNull()
            if (code.isEmpty() || millis == null) null else code to millis
        }.toMap()

internal fun encodeKeepUntil(keepUntil: Map<String, Long>): Set<String> =
    keepUntil.mapTo(HashSet()) { (code, millis) -> "$code$KEEP_UNTIL_SEPARATOR$millis" }
