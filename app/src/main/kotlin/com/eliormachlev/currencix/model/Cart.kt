package com.eliormachlev.currencix.model

/**
 * One line in a shopping cart: a display [name] and a raw calculator
 * [expression] like `"1.99"`, `"2 × 3.50"`, or `"10 + 5%"`. The expression
 * is stored verbatim so the user can re-edit it — evaluation happens at
 * display time via `String.evaluateCalculatorExpression()`.
 *
 * [pinned] items float to the top of the list at render time (sort happens
 * in the composable, not in storage) so the user's underlying insertion /
 * reorder order is preserved when they toggle pins on and off.
 */
data class CartItem(
    val id: String,
    val name: String,
    val expression: String,
    val pinned: Boolean = false,
)

/**
 * These items (storage order) after a drag dropped [movedId] where it sits
 * in [displayOrder] (the on-screen, pinned-first order), [pinned] or not.
 * Only the moved item changes place: it goes just before the next item of
 * its group on screen (or just after the previous one), so every other
 * item keeps its storage slot and unpinning still returns items to their
 * own order. Alone in its group, it keeps its slot. Unknown ids leave the
 * list unchanged.
 */
internal fun List<CartItem>.afterDrop(
    displayOrder: List<String>,
    movedId: String,
    pinned: Boolean,
): List<CartItem> {
    val moved = firstOrNull { it.id == movedId } ?: return this
    val pinnedById = associate { it.id to it.pinned }
    val group = displayOrder.filter { it == movedId || pinnedById[it] == pinned }
    val at = group.indexOf(movedId)
    val rest = filterNot { it.id == movedId }
    val next = group.getOrNull(at + 1)?.let { id -> rest.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
    val prev = group.getOrNull(at - 1)?.let { id -> rest.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
    val slot = next ?: prev?.plus(1) ?: indexOf(moved)
    return rest.toMutableList().apply { add(slot.coerceIn(0, size), moved.copy(pinned = pinned)) }
}

/**
 * A named, persisted cart. The "current" (session) cart is stored under its
 * own preferences key with an empty [id]/[name] — only carts the user has
 * explicitly saved get a real id and name. Currencies are stored as ISO-4217
 * alpha codes (not [Currency]) so a legacy or unknown code round-trips
 * through disk without dropping the cart.
 *
 * [currency] is the base — items' prices are entered in this. [destinationCurrency]
 * is the currency the total is displayed in; `null` means "same as base" (no
 * conversion). A cart with `destinationCurrency == null` collapses to the
 * pre-conversion single-currency behavior.
 */
data class SavedCart(
    val id: String,
    val name: String,
    val currency: String,
    val items: List<CartItem>,
    val createdAt: Long,
    val destinationCurrency: String? = null,
)
