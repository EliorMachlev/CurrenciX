package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.repository.json.MalformedJsonException
import com.eliormachlev.currencix.repository.json.readJson
import com.eliormachlev.currencix.repository.json.writeJson
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.util.DroppedCacheEntries
import com.eliormachlev.currencix.util.toLocalDateOrNull
import com.squareup.moshi.JsonReader
import okio.BufferedSource
import okio.buffer
import okio.source
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// Under filesDir, not cacheDir: years of history take many requests to fetch
// again, so the system shouldn't clear them to free space.
private const val TIMELINES_DIR = "timelines"
private const val FILE_EXTENSION = ".json"
private const val TMP_SUFFIX = ".tmp"
private const val TAG = "TimelineFiles"

// The pairs parsed most recently, kept in memory: the chart and the widget
// read the same few again and again.
private const val MEMORY_ENTRIES = 8

/**
 * Each provider's history for each pair, one file apiece
 * (`<providerId>_<BASE>_<SYMBOL>.json`, a flat {date → rate} JSON map),
 * so saving one pair's history rewrites that file alone. They used to share
 * one preferences file, held whole in memory and rewritten on every save;
 * what's left there moves into the files on first use.
 *
 * Writes go to a `.tmp` file renamed over the old one, so a crash leaves the
 * old history or the new one, never half of it. Reads hit the disk, so call
 * these off the main thread.
 */
internal class TimelineFiles private constructor(
    private val dir: File,
    private val legacy: PrefStore,
) {
    private val memory =
        object : LinkedHashMap<String, Timeline>(MEMORY_ENTRIES, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Timeline>) = size > MEMORY_ENTRIES
        }

    // The old shared file moves over once, before the first read or write.
    private val migrated by lazy { migrateLegacy() }

    /** Moves the old shared file over now, off the main thread (app start). */
    fun warmUp() {
        migrated
    }

    fun read(
        provider: ApiProvider,
        base: Currency,
        symbol: Currency,
    ): Timeline? {
        migrated
        val name = fileName(provider.id, base, symbol)
        synchronized(memory) { memory[name] }?.let { return it }
        val file = File(dir, name)
        if (!file.isFile) return null
        val timeline =
            try {
                file.source().buffer().use { decode(it, provider, base, symbol) }
            } catch (e: IOException) {
                Timber.tag(TAG).w(e, "Couldn't read %s", name)
                null
            }
        if (timeline == null) DroppedCacheEntries.record(TAG)
        timeline?.let { synchronized(memory) { memory[name] = it } }
        return timeline
    }

    fun write(
        timeline: Timeline,
        base: Currency,
        symbol: Currency,
    ) {
        migrated
        val provider = timeline.provider ?: return
        val rates = timeline.rates?.takeIf { it.isNotEmpty() } ?: return
        val name = fileName(provider.id, base, symbol)
        writeAtomically(name, encode(rates))
        synchronized(memory) { memory[name] = timeline }
    }

    private fun writeAtomically(
        name: String,
        text: String,
    ) {
        val target = File(dir, name)
        val tmp = File(dir, name + TMP_SUFFIX)
        try {
            dir.mkdirs()
            tmp.writeText(text)
            // Some filesystems won't rename over an existing file.
            if (!tmp.renameTo(target) && !(target.delete() && tmp.renameTo(target))) throw IOException("Rename failed for $name")
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Couldn't write %s", name)
            tmp.delete()
        }
    }

    // Moves each history out of the old preferences file into its own, then
    // empties the old file.
    private fun migrateLegacy() {
        val entries = legacy.snapshot().asMap()
        if (entries.isEmpty()) return
        entries.forEach { (key, value) -> migrateEntry(key, value) }
        legacy.edit { clear() }
    }

    private fun migrateEntry(
        key: Preferences.Key<*>,
        value: Any,
    ) {
        val json = value as? String ?: return
        // The old key: "<providerId>|<BASE>|<SYMBOL>".
        val parts = key.name.split(LEGACY_SEPARATOR)
        val providerId = parts.getOrNull(0)?.toIntOrNull() ?: return
        val base = parts.getOrNull(1)?.let(Currency::fromString) ?: return
        val symbol = parts.getOrNull(2)?.let(Currency::fromString) ?: return
        writeAtomically(fileName(providerId, base, symbol), json)
    }

    companion object {
        private const val LOAD_FACTOR = 0.75f
        private const val LEGACY_SEPARATOR = "|"

        private val instances = ConcurrentHashMap<String, TimelineFiles>()

        // One per folder (so one per process in the app; tests each get a
        // fresh folder), so every reader sees the same memory cache.
        fun get(context: Context): TimelineFiles {
            val app = context.applicationContext
            val dir = File(app.filesDir, TIMELINES_DIR)
            return instances.computeIfAbsent(dir.absolutePath) { TimelineFiles(dir, PersistenceKey.TIMELINES.prefStore(app)) }
        }

        private fun fileName(
            providerId: Int,
            base: Currency,
            symbol: Currency,
        ): String = "${providerId}_${base.iso4217Alpha()}_${symbol.iso4217Alpha()}$FILE_EXTENSION"

        // Rates as plain strings, so no precision is lost to a JSON double.
        private fun encode(rates: Map<LocalDate, Rate>): String =
            writeJson { writer ->
                writer.beginObject()
                rates.forEach { (date, rate) -> writer.name(date.toString()).value(rate.value.toPlainString()) }
                writer.endObject()
            }

        // Null when the text isn't a history (it's then fetched again).
        // Streamed: a history runs to thousands of days.
        private fun decode(
            source: BufferedSource,
            provider: ApiProvider,
            base: Currency,
            symbol: Currency,
        ): Timeline? {
            val values =
                try {
                    readJson(source, ::readDatedValues)
                } catch (e: MalformedJsonException) {
                    Timber.tag(TAG).w(e, "Malformed history for %s/%s", base, symbol)
                    return null
                }
            if (values.isEmpty()) return null
            val rates = values.mapValuesTo(sortedMapOf<LocalDate, Rate>()) { (_, value) -> Rate(symbol, value) }
            return Timeline(
                success = true,
                error = null,
                base = base.iso4217Alpha(),
                startDate = rates.firstKey(),
                endDate = rates.lastKey(),
                rates = rates,
                provider = provider,
            )
        }
    }
}

// A flat {date → rate} object; entries that aren't a date and a number are skipped.
private fun readDatedValues(reader: JsonReader): Map<LocalDate, BigDecimal> {
    val values = HashMap<LocalDate, BigDecimal>()
    reader.beginObject()
    while (reader.hasNext()) {
        val date = reader.nextName().toLocalDateOrNull()
        val value =
            if (reader.peek() in RATE_TOKENS) {
                reader.nextString().toBigDecimalOrNull()
            } else {
                reader.skipValue()
                null
            }
        if (date != null && value != null) values[date] = value
    }
    reader.endObject()
    return values
}

// The tokens a rate can be: a string (as written) or a bare number (hand-edited).
private val RATE_TOKENS = setOf(JsonReader.Token.STRING, JsonReader.Token.NUMBER)
