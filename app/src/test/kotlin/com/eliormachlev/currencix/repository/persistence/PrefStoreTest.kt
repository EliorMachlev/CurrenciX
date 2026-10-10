package com.eliormachlev.currencix.repository.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executor

// Reads see the store's own writes, in order, even while the disk catches
// up: a disk emission that reflects only the first of two quick writes
// doesn't undo the second in the cache.
class PrefStoreTest {
    private val disk = GatedDataStore()

    // A same-thread dispatcher: the store's write pump and disk collector run
    // right away, on the test thread, so each step below is deterministic.
    private val scope = CoroutineScope(Job() + Executor(Runnable::run).asCoroutineDispatcher())
    private val store = PrefStore(disk, scope)

    @After
    fun tearDown() = scope.cancel()

    private fun value() = store.snapshot()[KEY]

    @Test
    fun `a write that lands first doesn't undo a later one in the cache`() {
        store.edit { this[KEY] = "A" }
        store.edit { this[KEY] = "B" }
        assertEquals("B", value())

        disk.landNextWrite()
        // The disk now says "A": the store still has "B" on its way.
        assertEquals("A", disk.current[KEY])
        assertEquals("B", value())

        disk.landNextWrite()
        assertEquals("B", disk.current[KEY])
        assertEquals("B", value())
    }

    @Test
    fun `with no write pending, a change on disk reaches the cache`() {
        store.edit { this[KEY] = "A" }
        disk.landNextWrite()

        disk.current = disk.current.toMutablePreferences().apply { this[KEY] = "restored" }

        assertEquals("restored", value())
    }

    // A DataStore whose writes wait until the test lets each one land.
    private class GatedDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val permits = Channel<Unit>(Channel.UNLIMITED)

        var current: Preferences
            get() = state.value
            set(value) {
                state.value = value
            }

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            permits.receive()
            val next = transform(state.value)
            state.value = next
            return next
        }

        fun landNextWrite() {
            permits.trySend(Unit)
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("key")
    }
}
