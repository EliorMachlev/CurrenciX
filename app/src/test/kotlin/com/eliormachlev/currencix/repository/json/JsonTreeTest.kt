package com.eliormachlev.currencix.repository.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// The JSON the app writes for itself, read and written without org.json.
class JsonTreeTest {
    @Test
    fun `fields read leniently, the way org-json read them`() {
        val obj = parseJsonObject("""{"s":"x","n":42,"f":2.5,"b":true,"bs":"TRUE","ns":"7","o":{"k":1},"a":[{"k":2},3]}""")
        assertEquals("x", obj.optString("s"))
        assertEquals("a whole number reads without .0", "42", obj.optString("n"))
        assertEquals("2.5", obj.optString("f"))
        assertEquals(42, obj.optInt("n"))
        assertEquals("a fraction is cut off", 2, obj.optInt("f"))
        assertEquals("a number in a string", 7, obj.optInt("ns"))
        assertEquals(-1, obj.optInt("missing", -1))
        assertEquals(-1, obj.optInt("s", -1))
        assertTrue(obj.optBoolean("b"))
        assertTrue(obj.optBoolean("bs"))
        assertFalse(obj.optBoolean("missing"))
        assertEquals(1, obj.optObject("o")?.optInt("k"))
        assertNull(obj.optObject("s"))
        assertEquals("entries that aren't objects are skipped", listOf(2), obj.optObjects("a").map { it.optInt("k") })
        assertNull(obj.optStringOrNull("missing"))
        assertEquals(listOf("s", "n", "f", "b", "bs", "ns", "o", "a"), obj.keys)
    }

    @Test
    fun `big whole numbers keep every digit`() {
        val millis = 1_761_000_000_123L
        assertEquals(millis, parseJsonObject(jsonText(mapOf("t" to millis))).optLong("t"))
    }

    @Test
    fun `what is written reads back the same`() {
        val value = mapOf("id" to "a\"b/ü", "n" to 3, "on" to true, "list" to listOf(mapOf("k" to "v")), "set" to listOf("x", "y"))
        listOf(false, true).forEach { pretty ->
            val obj = parseJsonObject(jsonText(value, pretty))
            assertEquals("a\"b/ü", obj.optString("id"))
            assertEquals(3, obj.optInt("n"))
            assertTrue(obj.optBoolean("on"))
            assertEquals("v", obj.optObjects("list").single().optString("k"))
            assertEquals(listOf("x", "y"), obj.optArray("set"))
        }
        assertTrue("pretty text is indented", jsonText(value, pretty = true).contains("\n  \"id\""))
    }

    @Test
    fun `text that isn't the JSON asked for is malformed`() {
        listOf("", "{", "nope", "{\"a\":1} trailing", "[1,2]", "{\"a\":1,\"a\":2}").forEach { text ->
            assertThrows(text, MalformedJsonException::class.java) { parseJsonObject(text) }
        }
        assertThrows(MalformedJsonException::class.java) { parseJsonArray("{}") }
        assertEquals(listOf(1.0, "x"), parseJsonArray("[1, \"x\"]"))
    }
}
