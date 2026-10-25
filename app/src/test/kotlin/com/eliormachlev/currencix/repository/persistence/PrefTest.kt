package com.eliormachlev.currencix.repository.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executor

// A setting whose stored type changed between builds (the decimal places went
// from a String to an Int) reads as unset on a phone that still has the old
// value, instead of failing to cast, and the next write replaces it.
class PrefTest {
    private val scope = CoroutineScope(Job() + Executor(Runnable::run).asCoroutineDispatcher())

    @After
    fun tearDown() = scope.cancel()

    private fun storeHolding(vararg pairs: Preferences.Pair<*>) = PrefStore(MemoryDataStore(preferencesOf(*pairs)), scope)

    @Test
    fun `a value stored as another type reads as unset`() {
        val store = storeHolding(stringPreferencesKey(NAME) to "4")
        val pref = store.intPref(NAME, decode = { it ?: DEFAULT }, encode = { it })

        assertEquals(DEFAULT, pref.get())
    }

    @Test
    fun `the next write replaces a value stored as another type`() {
        val store = storeHolding(stringPreferencesKey(NAME) to "4")
        val pref = store.intPref(NAME, decode = { it ?: DEFAULT }, encode = { it })

        pref.set(SAVED)

        assertEquals(SAVED, pref.get())
        assertEquals(SAVED, store.snapshot()[intPreferencesKey(NAME)])
    }

    @Test
    fun `a value of the right type is read as stored`() {
        val store = storeHolding(intPreferencesKey(NAME) to SAVED)

        assertEquals(SAVED, store.intPref(NAME, decode = { it ?: DEFAULT }, encode = { it }).get())
    }

    // A DataStore that applies each write at once.
    private class MemoryDataStore(
        initial: Preferences,
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    private companion object {
        const val NAME = "_decimalPlaces"
        const val DEFAULT = 2
        const val SAVED = 3
    }
}
