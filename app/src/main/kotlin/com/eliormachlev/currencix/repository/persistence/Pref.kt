package com.eliormachlev.currencix.repository.persistence

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One setting or saved value, read and written as the app's type [T]: the
 * current value ([get]), a new one ([set]), and changes as a [flow] or
 * [liveData]. All of them go through the same read and write, so a value's
 * default, its stored shape and where it's stored live in one declaration.
 *
 * Settings stores declare one per setting with the factories below
 * (`store.booleanPref(KEY, default = true)`), so adding a setting is one line.
 * A [CachedStore] of any kind (preferences or a Protobuf file) makes them
 * with [statePref].
 */
class Pref<T> internal constructor(
    private val read: () -> T,
    private val write: (T) -> Unit,
    private val change: ((T) -> T) -> Unit,
    changes: () -> Flow<T>,
    // Runs after every write (the widget redraws when its pair changes).
    private val afterWrite: () -> Unit = {},
) {
    /** The current value, from the in-memory cache (see [CachedStore.snapshot]). */
    fun get(): T = read()

    /** Stores [value]: visible to [get] at once, on disk shortly after. */
    fun set(value: T) {
        write(value)
        afterWrite()
    }

    /** Replaces the value with [transform] of it, in one edit (no other write lands in between). */
    fun update(transform: (T) -> T) {
        change(transform)
        afterWrite()
    }

    /** The value, then each change to it. */
    val flow: Flow<T> by lazy(changes)

    /** [flow] for View-system and LiveData observers. */
    val liveData: LiveData<T> by lazy { flow.asLiveData() }
}

/** A value read out of the store's whole state by [read], and put back into it by [write]. */
internal fun <S, T> CachedStore<S>.statePref(
    read: (S) -> T,
    write: (S, T) -> S,
    afterWrite: () -> Unit = {},
): Pref<T> =
    Pref(
        read = { read(snapshot()) },
        write = { value -> update { state -> write(state, value) } },
        change = { transform -> update { state -> write(state, transform(read(state))) } },
        changes = { mappedFlow(read) },
        afterWrite = afterWrite,
    )

/**
 * A value kept in one [field] of the store's state, as [decode] reads it:
 * decoded again only when the field changes. [write] puts a new value back.
 */
internal fun <S, F, T> CachedStore<S>.fieldPref(
    field: (S) -> F,
    decode: (F) -> T,
    write: (S, T) -> S,
): Pref<T> {
    val decoded = LastResult<F, T>(same = { a, b -> a === b }, compute = decode)
    return statePref(read = { decoded(field(it)) }, write = write)
}

/** This value seen as [T]: read through [decode], written through [encode]. */
internal fun <S, T> Pref<S>.mapped(
    decode: (S) -> T,
    encode: (T) -> S,
): Pref<T> =
    Pref(
        read = { decode(get()) },
        write = { set(encode(it)) },
        change = { transform -> update { encode(transform(decode(it))) } },
        changes = { flow.map(decode) },
    )

/** A setting read from the whole preference set and written by [write] (one derived from others). */
internal fun <T> PrefStore.pref(
    read: (Preferences) -> T,
    write: MutablePreferences.(T) -> Unit,
    afterWrite: () -> Unit = {},
): Pref<T> = statePref(read, { prefs, value -> prefs.edited { write(value) } }, afterWrite)

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

/** A set of strings, empty when unset (and removed when set empty). */
internal fun PrefStore.stringSetPref(name: String): Pref<Set<String>> =
    keyedPref(stringSetPreferencesKey(name), { it.orEmpty() }, { set -> set.takeIf { it.isNotEmpty() } })

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
