package com.eliormachlev.currencix.viewmodel.cart

import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.afterDrop
import com.eliormachlev.currencix.util.newId

/**
 * Edits to the working cart's rows ([CartViewModel.rows]). Each hands
 * [mutate] a change to the cart; returning the same cart means "nothing
 * changed", which skips the emission and the write.
 */
class CartRowEdits internal constructor(
    private val mutate: ((SavedCart) -> SavedCart) -> Unit,
) {
    /** Adds an item at the end; returns its id. */
    fun add(
        name: String,
        expression: String,
    ): String {
        val id = newId()
        mutate { cart -> cart.copy(items = cart.items + CartItem(id = id, name = name, expression = expression)) }
        return id
    }

    fun update(
        id: String,
        name: String,
        expression: String,
    ) = mutateItem(id) { it.copy(name = name, expression = expression) }

    fun remove(id: String) {
        mutate { cart ->
            val filtered = cart.items.filter { it.id != id }
            if (filtered.size == cart.items.size) cart else cart.copy(items = filtered)
        }
    }

    /**
     * Puts a removed [item] back at [index] (clamped to the list) — Undo for
     * a delete. No-op if an item with its id is already there.
     */
    fun restore(
        item: CartItem,
        index: Int,
    ) {
        mutate { cart ->
            if (cart.items.any { it.id == item.id }) return@mutate cart
            val items = cart.items.toMutableList()
            items.add(index.coerceIn(0, items.size), item)
            cart.copy(items = items)
        }
    }

    fun togglePinned(id: String) = mutateItem(id) { it.copy(pinned = !it.pinned) }

    /** Adds a copy of item [id] (a new id, not in the basket) right after it. */
    fun duplicate(id: String) {
        mutate { cart ->
            val index = cart.items.indexOfFirst { it.id == id }
            if (index < 0) return@mutate cart
            val copy = cart.items[index].copy(id = newId(), checked = false)
            cart.copy(items = cart.items.toMutableList().apply { add(index + 1, copy) })
        }
    }

    /** Moves item [id] to the top of its section (pinned or not): first in storage. */
    fun moveToTop(id: String) {
        mutate { cart ->
            val item = cart.items.firstOrNull { it.id == id } ?: return@mutate cart
            if (cart.items.first() === item) return@mutate cart
            cart.copy(items = listOf(item) + cart.items.filterNot { it.id == id })
        }
    }

    fun setQuantity(
        id: String,
        quantity: Int,
    ) = mutateItem(id) { it.copy(quantity = quantity.coerceIn(CartItem.MIN_QUANTITY, CartItem.MAX_QUANTITY)) }

    /** Item [id] is for [personId], or shared by everyone (null), when the cart splits by person. */
    fun assignTo(
        id: String,
        personId: String?,
    ) = mutateItem(id) { it.copy(personId = personId) }

    /** Ticks item [id] as in the basket, or unticks it (shopping-list mode). */
    fun toggleChecked(id: String) = mutateItem(id) { it.copy(checked = !it.checked) }

    /**
     * Applies a finished drag: [movedId] was dropped where it sits in
     * [displayOrder] (see [afterDrop]). A release without movement changes
     * nothing and skips the write.
     */
    fun commitDrag(
        displayOrder: List<String>,
        movedId: String,
    ) {
        mutate { cart ->
            val items = cart.items.afterDrop(displayOrder, movedId)
            if (items == cart.items) cart else cart.copy(items = items)
        }
    }

    // Replaces item [id] with [transform] of it; no item, no change.
    private inline fun mutateItem(
        id: String,
        crossinline transform: (CartItem) -> CartItem,
    ) {
        mutate { cart ->
            val index = cart.items.indexOfFirst { it.id == id }
            if (index < 0) return@mutate cart
            val updated = transform(cart.items[index])
            if (updated == cart.items[index]) cart else cart.copy(items = cart.items.toMutableList().apply { set(index, updated) })
        }
    }
}
