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
 * in [displayOrder] (the on-screen, pinned-first order). A drag stays within
 * the row's group (pinned or not), so only its place changes: it goes just
 * before the next row of its group on screen (or just after the previous
 * one). Every other item keeps its storage slot, so unpinning still returns
 * items to their own order. Unknown ids leave the list unchanged.
 */
internal fun List<CartItem>.afterDrop(
    displayOrder: List<String>,
    movedId: String,
): List<CartItem> {
    val moved = firstOrNull { it.id == movedId } ?: return this
    val pinnedById = associate { it.id to it.pinned }
    val group = displayOrder.filter { pinnedById[it] == moved.pinned }
    // Already in that order (a release without movement): leave every slot alone.
    if (group == filter { it.pinned == moved.pinned }.map { it.id }) return this
    val at = group.indexOf(movedId)
    val rest = filterNot { it.id == movedId }
    val next = group.getOrNull(at + 1)?.let { id -> rest.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
    val prev = group.getOrNull(at - 1)?.let { id -> rest.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
    val slot = next ?: prev?.plus(1) ?: indexOf(moved)
    return rest.toMutableList().apply { add(slot.coerceIn(0, size), moved) }
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
