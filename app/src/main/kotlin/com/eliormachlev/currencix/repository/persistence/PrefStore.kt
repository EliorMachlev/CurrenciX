package com.eliormachlev.currencix.repository.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * Thin wrapper around a per-namespace [DataStore] that preserves the
 * synchronous-read / fire-and-forget-write ergonomics the app relied on with
 * SharedPreferences, while making DataStore the source of truth on disk.
 *
 * Read path — reads hit an in-memory [MutableStateFlow] cache that mirrors the
 * DataStore's `.data` flow. The cache bootstraps once, lazily, via
 * `runBlocking { store.data.first() }` on first read; DataStore's disk read is
 * a single small `.preferences_pb` file (~1 KB per namespace here) so this
 * blocking cost is bounded and pays for itself by removing the
 * `warmSharedPreferences()` pre-warm dance the SharedPreferences path needed.
 *
 * Write path — writes update the in-memory cache synchronously so subsequent
 * blocking reads on the same thread see the new value, then a background
 * coroutine drains a serial [writeQueue] that persists each mutation via
 * `store.edit { }`. Serializing the queue keeps write ordering intact even
 * under bursts (the "insertExchangeRates writes ~180 keys" case).
 *
 * While this store still has writes on their way to disk, the disk's own
 * emissions are older than the cache (each one reflects only the writes
 * finished so far), so they're ignored; once the last pending write lands,
 * the cache is resynced from disk. Without that, a read between two quick
 * writes could see the first one undone.
 */
class PrefStore internal constructor(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope = defaultScope,
) {
    private val cache = MutableStateFlow<Preferences?>(null)

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
        MutableSharedFlow<suspend MutablePreferences.() -> Unit>(
            extraBufferCapacity = WRITE_QUEUE_CAPACITY,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )

    init {
        // Persist queued mutations in submission order.
        scope.launch {
            writeQueue.collect { mutator ->
                try {
                    runCatching { store.edit { mutator(it) } }
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
     * Snapshot the current preferences, blocking on the first call until
     * DataStore's initial disk read completes. Subsequent calls read from the
     * hot [cache] without touching disk.
     */
    fun snapshot(): Preferences {
        cache.value?.let { return it }
        val initial = runBlocking { store.data.first() }
        cache.value = initial
        return initial
    }

    /**
     * Snapshot state as an observable [MutableStateFlow]. Guaranteed non-null
     * after [snapshot] has been called at least once on any thread; consumers
     * that map keys off this flow should call [snapshot] first (or accept a
     * transient `null` seed) to guarantee the initial value.
     */
    val flow: Flow<Preferences> get() = cache.asStateFlow().map { it ?: snapshot() }

    fun <T> mappedFlow(mapper: (Preferences) -> T): Flow<T> = flow.map(mapper).distinctUntilChanged()

    fun <T> mappedLiveData(mapper: (Preferences) -> T): LiveData<T> = mappedFlow(mapper).asLiveData()

    // Applies [mutator] to the cache and counts it as pending until it
    // reaches disk.
    private fun applyToCache(mutator: MutablePreferences.() -> Unit) {
        synchronized(cacheWriteLock) {
            val next = snapshot().toMutablePreferences()
            next.mutator()
            cache.value = next
            pendingWrites++
        }
    }

    private fun syncFromDisk(disk: Preferences) {
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
     * Queue a mutation and eagerly apply it to the in-memory cache so
     * subsequent [snapshot] reads on the calling thread observe the new value.
     * The actual disk write is executed asynchronously on the background scope.
     */
    fun edit(mutator: MutablePreferences.() -> Unit) {
        applyToCache(mutator)
        // A full queue would otherwise drop the write: wait for room instead.
        if (!writeQueue.tryEmit(mutator)) scope.launch { writeQueue.emit(mutator) }
    }

    /**
     * Suspend variant used by BackupManager when it needs to await the disk
     * write. Bypasses [writeQueue] to give the caller a completion signal;
     * still mirrors the mutation into the cache first for read-your-writes.
     */
    suspend fun editAndAwait(mutator: MutablePreferences.() -> Unit) {
        applyToCache(mutator)
        try {
            store.edit { it.mutator() }
        } finally {
            onWriteLanded()
        }
    }

    private companion object {
        const val WRITE_QUEUE_CAPACITY = 256

        // Single supervised, IO-dispatched scope shared by every PrefStore
        // instance. Long-lived (process scope) — DataStore itself is a process
        // singleton, so binding the write pump to the same lifetime is safe.
        val defaultScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

// Process-singleton cache. DataStore itself is a process singleton per file
// name, and PrefStore wraps stateful coroutine machinery (write pump, cache
// collector) — spinning up a fresh instance on every `Database(context)`
// construction would leak collectors. Keyed by PersistenceKey since context
// isn't part of the identity (application context is used inside DataStore).
private val instances = java.util.concurrent.ConcurrentHashMap<PersistenceKey, PrefStore>()

fun PersistenceKey.prefStore(context: Context): PrefStore =
    instances.getOrPut(this) {
        // Use application context to avoid pinning an Activity in the
        // process-lifetime coroutine that owns the write pump.
        PrefStore(dataStore(context.applicationContext))
    }
