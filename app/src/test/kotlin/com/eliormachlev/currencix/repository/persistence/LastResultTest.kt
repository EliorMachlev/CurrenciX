package com.eliormachlev.currencix.repository.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LastResultTest {
    @Test
    fun `the same input answers from memory, a new one computes`() {
        var computed = 0
        val parse =
            LastResult<String?, List<String>>(same = { a, b -> a == b }) {
                computed++
                it?.split(",").orEmpty()
            }

        val first = parse("a,b")
        assertSame(first, parse(String(charArrayOf('a', ',', 'b'))))
        assertEquals(1, computed)

        assertEquals(listOf("c"), parse("c"))
        assertEquals(emptyList<String>(), parse(null))
        assertEquals(emptyList<String>(), parse(null))
        assertEquals(3, computed)
    }

    @Test
    fun `identity memo recomputes for an equal but different object`() {
        var computed = 0
        val encode =
            LastResult<List<Int>, String>(same = { a, b -> a === b }) {
                computed++
                it.joinToString()
            }
        val value = listOf(1, 2)
        encode(value)
        encode(value)
        encode(listOf(1, 2))
        assertEquals(2, computed)
    }
}
