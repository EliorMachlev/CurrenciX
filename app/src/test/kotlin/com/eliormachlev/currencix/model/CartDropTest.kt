package com.eliormachlev.currencix.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CartDropTest {
    private fun item(
        id: String,
        pinned: Boolean = false,
    ) = CartItem(id = id, name = id, expression = "1", pinned = pinned)

    private fun List<CartItem>.summary() = joinToString(" ") { if (it.pinned) "${it.id}*" else it.id }

    @Test
    fun `a pinned row reorders among the pinned ones`() {
        // Storage: a* b* c. Drag a under b.
        val storage = listOf(item("a", true), item("b", true), item("c"))
        assertEquals("b* a* c", storage.afterDrop(listOf("b", "a", "c"), movedId = "a").summary())
    }

    @Test
    fun `only the moved row changes storage slot`() {
        // Storage: x a* y z (a* shows first on screen). Drag z above y.
        val storage = listOf(item("x"), item("a", true), item("y"), item("z"))
        assertEquals("x a* z y", storage.afterDrop(listOf("a", "x", "z", "y"), movedId = "z").summary())
    }

    @Test
    fun `dropped last in its group, it goes after the previous row of the group`() {
        val storage = listOf(item("x"), item("y"), item("a", true))
        assertEquals("y x a*", storage.afterDrop(listOf("a", "y", "x"), movedId = "x").summary())
    }

    @Test
    fun `a release without movement leaves the order as it was`() {
        val storage = listOf(item("x"), item("a", true), item("y"))
        assertSame(storage, storage.afterDrop(listOf("a", "x", "y"), movedId = "x"))
    }

    @Test
    fun `an unknown id changes nothing`() {
        val storage = listOf(item("a"), item("b"))
        assertSame(storage, storage.afterDrop(listOf("a", "b"), movedId = "zzz"))
    }
}
