package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.runtime.mutableStateListOf
import com.eliormachlev.currencix.model.CartItem
import org.junit.Assert.assertEquals
import org.junit.Test

// A drag never carries a row across the last pinned row: pinned rows stay
// above it, unpinned rows below it.
class CartDragGroupTest {
    // "a*" is pinned; the list is the on-screen, pinned-first order.
    private fun rows(vararg ids: String) =
        mutableStateListOf(
            *ids.map { CartItem(id = it.removeSuffix("*"), name = "", expression = "", pinned = it.endsWith("*")) }.toTypedArray(),
        )

    private fun List<CartItem>.summary() = joinToString(" ") { if (it.pinned) "${it.id}*" else it.id }

    @Test
    fun `rows reorder within their own group`() {
        val list = rows("a*", "b*", "c", "d")
        list.moveByKey("a", "b")
        list.moveByKey("d", "c")
        assertEquals("b* a* d c", list.summary())
    }

    @Test
    fun `a pinned row can't pass below the last pinned one`() {
        val list = rows("a*", "b*", "c")
        list.moveByKey("b", "c")
        assertEquals("a* b* c", list.summary())
    }

    @Test
    fun `an unpinned row can't pass above the last pinned one`() {
        val list = rows("a*", "c", "d")
        list.moveByKey("c", "a")
        assertEquals("a* c d", list.summary())
    }

    @Test
    fun `a section heading isn't a row to swap with`() {
        val list = rows("a*", "c")
        list.moveByKey("c", "section:others")
        assertEquals("a* c", list.summary())
    }
}
