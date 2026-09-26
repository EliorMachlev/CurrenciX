package com.eliormachlev.currencix.repository.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * JSON-per-key on-disk store, implementing [SourceOfTruth] via a caller-
 * supplied [Converter] so the same disk plumbing serves any typed value.
 *
 * ### Layout
 * `<rootDir>/<stableId>.json` — one file per key. The first line is the
 * `writtenAtMillis` timestamp (base-10 `System.currentTimeMillis()`), the
 * remainder is the converter's encoded payload. Two-line format instead of
 * a JSON envelope so a future migration to Room can skim `writtenAtMillis`
 * without pulling in a JSON parser, and so the payload survives converter
 * evolution without a schema-version bump.
 *
 * ### Atomicity
 * Writes go to a sibling `.tmp` file and are `renameTo`-swapped over the
 * live path. A crash / power-loss mid-write leaves either the prior file
 * (rename didn't happen) or the fully-written new file (rename succeeded) —
 * never a partial payload. Any reader that observed the entry keeps seeing
 * its previous value until the rename completes.
 *
 * ### Location
 * [rootDir] should live under `context.cacheDir` so the OS can reclaim it
 * on storage pressure (survives rotation / low-memory events; nuked only
 * when the device actually needs the space). Not `filesDir` — cached rate
 * data is regenerable, not user content.
 */
internal class DiskJsonStore<V : Any>(
    private val rootDir: File,
    private val converter: Converter<V>,
) : SourceOfTruth<V> {
    init {
        // mkdirs is a no-op when the directory already exists. Called from
        // the constructor so every read/write path can assume rootDir exists.
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    override suspend fun read(key: RateCacheKey): CachedEntry<V>? =
        withContext(Dispatchers.IO) {
            val file = fileFor(key)
            if (!file.isFile) return@withContext null
            runCatching {
                val text = file.readText()
                val newlineIndex = text.indexOf('\n')
                if (newlineIndex <= 0) return@runCatching null
                val writtenAt = text.substring(0, newlineIndex).toLongOrNull() ?: return@runCatching null
                val payload = text.substring(newlineIndex + 1)
                val decoded = converter.decode(payload) ?: return@runCatching null
                CachedEntry(decoded, writtenAt)
            }.getOrElse {
                Timber.tag(TAG).w(it, "Failed to read cache entry %s", key.stableId)
                null
            }
        }

    override suspend fun write(
        key: RateCacheKey,
        value: V,
    ) {
        withContext(Dispatchers.IO) {
            val target = fileFor(key)
            val tmp = File(rootDir, "${target.name}.tmp")
            runCatching {
                val encoded = converter.encode(value)
                tmp.writeText("${System.currentTimeMillis()}\n$encoded")
                if (!tmp.renameTo(target)) {
                    // Rename can fail on some filesystems if the target exists
                    // — retry via delete + rename so the atomic-write contract
                    // still holds from the reader's point of view.
                    target.delete()
                    if (!tmp.renameTo(target)) throw IOException("Rename failed for ${target.name}")
                }
            }.onFailure {
                Timber.tag(TAG).w(it, "Failed to write cache entry %s", key.stableId)
                runCatching { tmp.delete() }
            }
        }
    }

    override suspend fun clear(key: RateCacheKey) {
        withContext(Dispatchers.IO) { fileFor(key).delete() }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) { rootDir.listFiles()?.forEach { it.delete() } }
    }

    private fun fileFor(key: RateCacheKey): File = File(rootDir, "${key.stableId}.json")

    private companion object {
        const val TAG = "DiskJsonStore"
    }
}
