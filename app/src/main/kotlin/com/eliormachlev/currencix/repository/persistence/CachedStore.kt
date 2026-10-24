package com.eliormachlev.currencix.repository.persistence

import androidx.datastore.core.DataStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.eliormachlev.currencix.util.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * A [DataStore] of any kind (a preferences file, or a typed Protobuf one)
 * read synchronously and written fire-and-forget, with DataStore the source
 * of truth on disk. [PrefStore] is the preferences flavour; the saved carts,
 * trips and fees use it directly with their Protobuf messages.
 *
 * Read path — reads hit an in-memory [MutableStateFlow] cache that mirrors the
 * DataStore's `.data` flow. The cache bootstraps once, lazily, via
 * `runBlocking { store.data.first() }` on first read; each file is small, so
 * this blocking cost is bounded.
 *
 * Write path — writes update the in-memory cache synchronously so subsequent
 * blocking reads on the same thread see the new value, then a background
 * coroutine drains a serial [writeQueue] that persists each change via
 * `store.updateData { }`. Serializing the queue keeps write ordering intact
 * even under bursts (the "insertExchangeRates writes ~180 keys" case).
 *
 * While this store still has writes on their way to disk, the disk's own
 * emissions are older than the cache (each one reflects only the writes
 * finished so far), so they're ignored; once the last pending write lands,
 * the cache is resynced from disk. Without that, a read between two quick
 * writes could see the first one undone.
 */
open class CachedStore<S> internal constructor(
    private val store: DataStore<S>,
    private val scope: CoroutineScope = storeScope,
) {
    private val cache = MutableStateFlow<S?>(null)

    // Serializes the read-modify-write cycle on [cache] so two concurrent
    // callers can't clone the same "current" snapshot and then race their
    // updates back into cache in an order that loses one of them.
    // Declared before init: the collectors launched there take it, and with
    // an immediate dispatcher they run before the rest of the class is set up.
    private val cacheWriteLock = Any()

    // Writes applied to [cache] but not yet to disk. Changed only under
    // [cacheWriteLock], together with the cache.
    private var pendingWrites = 0

    // Serial write queue. Buffer is large enough to swallow a full
    // insertExchangeRates() burst without dropping; overflow is SUSPEND so a
    // callback that outruns disk still lands eventually.
    private val writeQueue =
        MutableSharedFlow<(S) -> S>(
            extraBufferCapacity = WRITE_QUEUE_CAPACITY,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )

    init {
        // Persist queued changes in submission order.
        scope.launch {
            writeQueue.collect { transform ->
                try {
                    runCatching { store.updateData(transform) }
                } finally {
                    onWriteLanded()
                }
            }
        }
        // Keep the cache aligned with disk. This picks up external writes
        // (e.g. BackupManager restore, migrations) that bypass the queue —
        // but not while our own writes are pending (see the class doc).
        scope.launch {
            store.data.collect { disk -> syncFromDisk(disk) }
        }
    }

    /**
     * Snapshot the current state, blocking on the first call until
     * DataStore's initial disk read completes. Subsequent calls read from the
     * hot [cache] without touching disk.
     */
    fun snapshot(): S {
        cache.value?.let { return it }
        val initial = runBlocking { store.data.first() }
        cache.value = initial
        return initial
    }

    /** The state, then each change to it. */
    val flow: Flow<S> get() = cache.asStateFlow().map { it ?: snapshot() }

    fun <T> mappedFlow(mapper: (S) -> T): Flow<T> = flow.map(mapper).distinctUntilChanged()

    fun <T> mappedLiveData(mapper: (S) -> T): LiveData<T> = mappedFlow(mapper).asLiveData()

    // Applies [transform] to the cache and counts it as pending until it
    // reaches disk.
    private fun applyToCache(transform: (S) -> S) {
        synchronized(cacheWriteLock) {
            cache.value = transform(snapshot())
            pendingWrites++
        }
    }

    private fun syncFromDisk(disk: S) {
        synchronized(cacheWriteLock) {
            if (pendingWrites == 0) cache.value = disk
        }
    }

    // A write reached disk (or failed). After the last one, the disk is the
    // truth again: resync, which also picks up any external write the
    // ignored emissions carried.
    private suspend fun onWriteLanded() {
        val drained = synchronized(cacheWriteLock) { --pendingWrites == 0 }
        if (drained) syncFromDisk(store.data.first())
    }

    /**
     * Replace the state with [transform] of it: in the in-memory cache at
     * once, so later [snapshot] reads on the calling thread see it, and on
     * disk asynchronously, on the background scope. [transform] runs on the
     * cache and again on the disk's state, so it must be pure.
     */
    fun update(transform: (S) -> S) {
        applyToCache(transform)
        // A full queue would otherwise drop the write: wait for room instead.
        if (!writeQueue.tryEmit(transform)) scope.launch { writeQueue.emit(transform) }
    }

    /**
     * [update], returning once the change is on disk (a restore awaits it
     * before restarting the app). Bypasses [writeQueue] to give the caller a
     * completion signal.
     */
    suspend fun updateAndAwait(transform: (S) -> S) {
        applyToCache(transform)
        try {
            store.updateData(transform)
        } finally {
            onWriteLanded()
        }
    }

    private companion object {
        const val WRITE_QUEUE_CAPACITY = 256
    }
}

// Single supervised, IO-dispatched scope shared by every store. Long-lived
// (process scope) — DataStore itself is a process singleton, so binding the
// write pump to the same lifetime is safe.
internal val storeScope: CoroutineScope = CoroutineScope(SupervisorJob() + AppDispatchers.production.io)
