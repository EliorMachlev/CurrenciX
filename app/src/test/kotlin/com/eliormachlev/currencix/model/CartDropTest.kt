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
    fun `dropping an item into the pinned group pins it without moving the others`() {
        // Storage: a b c*. On screen: c* a b. Drag b to the top.
        val storage = listOf(item("a"), item("b"), item("c", pinned = true))
        val after = storage.afterDrop(listOf("b", "c", "a"), movedId = "b", pinned = true)
        assertEquals("a b* c*", after.summary())
    }

    @Test
    fun `dragging a pinned item below the last pinned one unpins it`() {
        // Storage: a* b* c d. On screen the same. Drag a between c and d.
        val storage = listOf(item("a", true), item("b", true), item("c"), item("d"))
        val after = storage.afterDrop(listOf("b", "c", "a", "d"), movedId = "a", pinned = false)
        assertEquals("b* c a d", after.summary())
    }

    @Test
    fun `unpinned items keep their own order when a pin moves between them`() {
        // Storage: x a* y. On screen: a* x y. Unpin a by dropping it last.
        val storage = listOf(item("x"), item("a", true), item("y"))
        val after = storage.afterDrop(listOf("x", "y", "a"), movedId = "a", pinned = false)
        assertEquals("x y a", after.summary())
    }

    @Test
    fun `an item alone in its group keeps its storage slot`() {
        val storage = listOf(item("a"), item("b", true), item("c"))
        val after = storage.afterDrop(listOf("b", "a", "c"), movedId = "b", pinned = true)
        assertEquals(storage, after)
    }

    @Test
    fun `an unknown id changes nothing`() {
        val storage = listOf(item("a"), item("b"))
        assertSame(storage, storage.afterDrop(listOf("a", "b"), movedId = "zzz", pinned = true))
    }
}
