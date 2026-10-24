package com.eliormachlev.currencix.repository.persistence

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

/**
 * One setting in a [PrefStore], read and written as the app's type [T]: the
 * current value ([get]), a new one ([set]), and changes as a [flow] or
 * [liveData]. All four go through the same [read] and [write], so a setting's
 * default, its stored shape and its key live in one declaration.
 *
 * Settings stores declare one per setting with the factories below
 * (`store.booleanPref(KEY, default = true)`), so adding a setting is one line.
 */
class Pref<T> internal constructor(
    private val store: PrefStore,
    private val read: (Preferences) -> T,
    private val write: MutablePreferences.(T) -> Unit,
    // Runs after every write (the widget redraws when its pair changes).
    private val afterWrite: () -> Unit = {},
) {
    /** The current value, from the in-memory cache (see [PrefStore.snapshot]). */
    fun get(): T = read(store.snapshot())

    /** Stores [value]: visible to [get] at once, on disk shortly after. */
    fun set(value: T) {
        store.edit { write(value) }
        afterWrite()
    }

    /** Replaces the value with [transform] of it, in one edit (no other write lands in between). */
    fun update(transform: (T) -> T) {
        store.edit { write(transform(read(this))) }
        afterWrite()
    }

    /** The value, then each change to it. */
    val flow: Flow<T> by lazy { store.mappedFlow(read) }

    /** [flow] for View-system and LiveData observers. */
    val liveData: LiveData<T> by lazy { store.mappedLiveData(read) }
}

/** A setting read from the whole preference set and written by [write] (one derived from others). */
internal fun <T> PrefStore.pref(
    read: (Preferences) -> T,
    write: MutablePreferences.(T) -> Unit,
    afterWrite: () -> Unit = {},
): Pref<T> = Pref(this, read, write, afterWrite)

/** A setting stored under one [key], with [decode] / [encode] between the stored and the app's value. Null [encode] removes it. */
internal fun <S : Any, T> PrefStore.keyedPref(
    key: Preferences.Key<S>,
    decode: (S?) -> T,
    encode: (T) -> S?,
    afterWrite: () -> Unit = {},
): Pref<T> {
    // Decoded again only when the stored value changes (equal text), and
    // encoded once per write (the same value object): the cache and the disk
    // write both apply it, and every emission of the store re-reads it.
    val decoded = LastResult<S?, T>(same = { a, b -> a == b }, compute = decode)
    val encoded = LastResult<T, S?>(same = { a, b -> a === b }, compute = encode)
    return pref(
        read = { decoded(it[key]) },
        write = { value ->
            val stored = encoded(value)
            if (stored == null) remove(key) else this[key] = stored
        },
        afterWrite = afterWrite,
    )
}

internal fun PrefStore.booleanPref(
    name: String,
    default: Boolean,
): Pref<Boolean> = keyedPref(booleanPreferencesKey(name), { it ?: default }, { it })

internal fun PrefStore.stringPref(
    name: String,
    default: String,
): Pref<String> = keyedPref(stringPreferencesKey(name), { it ?: default }, { it })

/** A string setting with no default: null when unset, and setting null removes it. */
internal fun PrefStore.optionalStringPref(name: String): Pref<String?> = keyedPref(stringPreferencesKey(name), { it }, { it })

/** A setting stored as an Int ([encode]; null removes it) and read back through [decode], which gets null when unset. */
internal fun <T> PrefStore.intPref(
    name: String,
    decode: (Int?) -> T,
    encode: (T) -> Int?,
): Pref<T> = keyedPref(intPreferencesKey(name), decode, encode)
