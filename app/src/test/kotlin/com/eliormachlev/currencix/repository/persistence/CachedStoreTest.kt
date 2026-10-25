package com.eliormachlev.currencix.repository.persistence

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

// A disk read that fails is tried again: the cache keeps following the disk,
// and the failure never escapes into the process.
class CachedStoreTest {
    private val escaped = mutableListOf<Throwable>()
    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + thread + CoroutineExceptionHandler { _, e -> escaped += e })

    @After
    fun tearDown() {
        scope.cancel()
        thread.close()
    }

    // Its first read fails, as a file that's briefly unreadable does; the next ones work.
    private class FlakyDataStore : DataStore<String> {
        val disk = MutableStateFlow("first")
        val reads = AtomicInteger()

        override val data: Flow<String> =
            flow<String> { emitAll(disk) }.onStart { if (reads.incrementAndGet() == 1) throw IOException("unreadable") }

        override suspend fun updateData(transform: suspend (t: String) -> String): String = transform(disk.value).also { disk.value = it }
    }

    @Test
    fun `a failed read is retried and the cache follows the disk again`() {
        val disk = FlakyDataStore()
        val store = CachedStore(disk, scope)
        disk.disk.value = "changed outside"

        // The store's own disk collector meets the failed read (nothing has read yet but it)
        // and tries again a moment later.
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (disk.reads.get() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)

        assertEquals("changed outside", store.snapshot())
        assertTrue("retried", disk.reads.get() >= 2)
        assertTrue("nothing escaped: $escaped", escaped.isEmpty())
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 50L
    }
}
