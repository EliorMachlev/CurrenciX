package com.eliormachlev.currencix.repository.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * A per-namespace preferences [DataStore], read synchronously and written
 * fire-and-forget (see [CachedStore]), with edits written as changes to
 * [MutablePreferences], the way SharedPreferences edits were.
 */
class PrefStore internal constructor(
    store: DataStore<Preferences>,
    scope: CoroutineScope = storeScope,
) : CachedStore<Preferences>(store, scope) {
    /** Applies [mutator]: visible to [snapshot] at once, on disk shortly after. */
    fun edit(mutator: MutablePreferences.() -> Unit) = update { it.edited(mutator) }

    /** [edit], returning once the change is on disk. */
    suspend fun editAndAwait(mutator: MutablePreferences.() -> Unit) = updateAndAwait { it.edited(mutator) }
}

/** These preferences with [mutator] applied (a new set; this one is unchanged). */
internal fun Preferences.edited(mutator: MutablePreferences.() -> Unit): Preferences = toMutablePreferences().apply(mutator).toPreferences()

// Process-singleton cache. DataStore itself is a process singleton per file
// name, and PrefStore wraps stateful coroutine machinery (write pump, cache
// collector) — spinning up a fresh instance on every `Database(context)`
// construction would leak collectors. Keyed by PersistenceKey since context
// isn't part of the identity (application context is used inside DataStore).
private val instances = ConcurrentHashMap<PersistenceKey, PrefStore>()

fun PersistenceKey.prefStore(context: Context): PrefStore =
    instances.getOrPut(this) {
        // Use application context to avoid pinning an Activity in the
        // process-lifetime coroutine that owns the write pump.
        PrefStore(dataStore(context.applicationContext))
    }
