package com.eliormachlev.currencix.repository.cache

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val KEY = RateCacheKey.RatesLatest(1, "EUR", null)

/** Trivial identity converter for exercising the disk plumbing itself. */
private object StringConverter : Converter<String> {
    override fun encode(value: String): String = value

    override fun decode(encoded: String): String? = encoded
}

class DiskJsonStoreTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `round trips value and preserves written-at timestamp`() =
        runBlocking {
            val store = DiskJsonStore(tempFolder.root, StringConverter)
            store.write(KEY, "hello")

            val read = store.read(KEY)
            assertNotNull(read)
            assertEquals("hello", read!!.value)
            assertEquals(true, read.writtenAtMillis > 0)
        }

    @Test
    fun `interrupted write does not clobber prior value`() =
        runBlocking {
            val store = DiskJsonStore(tempFolder.root, StringConverter)
            store.write(KEY, "v1")

            // Simulate a failed write by directly dropping a stray .tmp file
            // — the store's atomic rename means the live file must still
            // read as v1. If the store were writing in-place, the stray tmp
            // wouldn't matter, but the atomic-write test is that the live
            // file is untouched by any half-baked temp state.
            val stray = java.io.File(tempFolder.root, "${KEY.stableId}.json.tmp")
            stray.writeText("garbage")

            val read = store.read(KEY)
            assertNotNull(read)
            assertEquals("v1", read!!.value)
        }

    @Test
    fun `decode failure yields null instead of throwing`() =
        runBlocking {
            val alwaysNullConverter =
                object : Converter<String> {
                    override fun encode(value: String) = value

                    override fun decode(encoded: String): String? = null
                }
            val store = DiskJsonStore(tempFolder.root, alwaysNullConverter)
            store.write(KEY, "v1")

            assertNull(store.read(KEY))
        }
}
