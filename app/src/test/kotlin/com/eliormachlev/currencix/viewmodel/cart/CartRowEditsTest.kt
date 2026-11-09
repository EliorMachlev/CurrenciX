package com.eliormachlev.currencix.viewmodel.cart

import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.SavedCart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CartRowEditsTest {
    private var cart = cartOf(item("a"), item("b"), item("c"))
    private var mutations = 0

    // Mirrors CartStateHolder: the same cart back means nothing changed.
    private val edits =
        CartRowEdits { change ->
            mutations++
            cart = change(cart)
        }

    private fun item(
        id: String,
        pinned: Boolean = false,
    ) = CartItem(id = id, name = "item $id", expression = "1", pinned = pinned)

    private fun cartOf(vararg items: CartItem) = SavedCart(id = "cart", name = "", currency = "USD", items = items.toList(), createdAt = 0)

    private val ids get() = cart.items.map { it.id }

    @Test
    fun `add appends an item and returns its id`() {
        val id = edits.add("milk", "2.50")

        assertEquals(4, cart.items.size)
        assertEquals(CartItem(id = id, name = "milk", expression = "2.50"), cart.items.last())
    }

    @Test
    fun `update changes name and expression of one item`() {
        edits.update("b", "tea", "3")

        assertEquals("tea", cart.items[1].name)
        assertEquals("3", cart.items[1].expression)
        assertEquals("item a", cart.items[0].name)
    }

    @Test
    fun `update of an unknown id leaves the same cart`() {
        val before = cart

        edits.update("missing", "x", "1")

        assertSame(before, cart)
    }

    @Test
    fun `update with identical values leaves the same cart`() {
        val before = cart

        edits.update("a", "item a", "1")

        assertSame(before, cart)
    }

    @Test
    fun `remove drops the item and ignores an unknown id`() {
        edits.remove("b")
        assertEquals(listOf("a", "c"), ids)

        val before = cart
        edits.remove("missing")
        assertSame(before, cart)
    }

    @Test
    fun `restore puts an item back at its index clamped to the list`() {
        edits.restore(item("x"), 1)
        assertEquals(listOf("a", "x", "b", "c"), ids)

        edits.restore(item("y"), 99)
        assertEquals("y", ids.last())

        edits.restore(item("z"), -5)
        assertEquals("z", ids.first())
    }

    @Test
    fun `restore of an item already there changes nothing`() {
        val before = cart

        edits.restore(item("b"), 0)

        assertSame(before, cart)
    }

    @Test
    fun `togglePinned flips the pin both ways`() {
        edits.togglePinned("a")
        assertTrue(cart.items[0].pinned)

        edits.togglePinned("a")
        assertFalse(cart.items[0].pinned)
    }

    @Test
    fun `duplicate adds an unticked copy with a new id right after`() {
        cart = cartOf(item("a").copy(checked = true), item("b"))

        edits.duplicate("a")

        assertEquals(3, cart.items.size)
        val copy = cart.items[1]
        assertNotEquals("a", copy.id)
        assertEquals("item a", copy.name)
        assertFalse(copy.checked)
        assertEquals("b", cart.items[2].id)
    }

    @Test
    fun `duplicate of an unknown id leaves the same cart`() {
        val before = cart

        edits.duplicate("missing")

        assertSame(before, cart)
    }

    @Test
    fun `moveToTop puts the item first, and the first or an unknown one changes nothing`() {
        edits.moveToTop("c")
        assertEquals(listOf("c", "a", "b"), ids)

        val before = cart
        edits.moveToTop("c")
        edits.moveToTop("missing")
        assertSame(before, cart)
    }

    @Test
    fun `setQuantity clamps to the allowed range`() {
        edits.setQuantity("a", 5)
        assertEquals(5, cart.items[0].quantity)

        edits.setQuantity("a", 0)
        assertEquals(CartItem.MIN_QUANTITY, cart.items[0].quantity)

        edits.setQuantity("a", 1000)
        assertEquals(CartItem.MAX_QUANTITY, cart.items[0].quantity)
    }

    @Test
    fun `assignTo sets and clears the person`() {
        edits.assignTo("a", "p1")
        assertEquals("p1", cart.items[0].personId)

        edits.assignTo("a", null)
        assertEquals(null, cart.items[0].personId)
    }

    @Test
    fun `toggleChecked ticks and unticks`() {
        edits.toggleChecked("a")
        assertTrue(cart.items[0].checked)

        edits.toggleChecked("a")
        assertFalse(cart.items[0].checked)
    }

    @Test
    fun `commitDrag reorders to the displayed order`() {
        edits.commitDrag(displayOrder = listOf("b", "c", "a"), movedId = "a")

        assertEquals(listOf("b", "c", "a"), ids)
    }

    @Test
    fun `commitDrag without movement leaves the same cart`() {
        val before = cart

        edits.commitDrag(displayOrder = listOf("a", "b", "c"), movedId = "b")

        assertSame(before, cart)
    }

    @Test
    fun `every edit goes through mutate once`() {
        edits.add("x", "1")
        edits.remove("x-none")
        edits.togglePinned("a")

        assertEquals(3, mutations)
    }
}
