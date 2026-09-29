package com.eliormachlev.currencix.view.cart.compose

import com.eliormachlev.currencix.model.CartItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandsPinnedTest {
    // "a*" is pinned; the list is the on-screen order after the drop.
    private fun order(vararg ids: String) =
        ids.map { CartItem(id = it.removeSuffix("*"), name = "", expression = "", pinned = it.endsWith("*")) }

    @Test
    fun `above the last pinned row it lands pinned`() {
        assertTrue(landsPinned(order("c", "a*", "b*"), "c"))
        assertTrue(landsPinned(order("a*", "c", "b*"), "c"))
    }

    @Test
    fun `below the last pinned row it lands unpinned`() {
        assertFalse(landsPinned(order("b*", "c", "a*"), "a"))
        assertFalse(landsPinned(order("c", "a*"), "a"))
    }

    @Test
    fun `right at the boundary it keeps what it was`() {
        // Reordering pins among themselves: a* goes just under b*.
        assertTrue(landsPinned(order("b*", "a*", "c"), "a"))
        // An unpinned row moved to the head of its group.
        assertFalse(landsPinned(order("a*", "d", "c"), "d"))
    }

    @Test
    fun `with nothing else pinned, the top slot keeps its state`() {
        assertTrue(landsPinned(order("a*", "b"), "a"))
        assertFalse(landsPinned(order("b", "a"), "b"))
    }
}
