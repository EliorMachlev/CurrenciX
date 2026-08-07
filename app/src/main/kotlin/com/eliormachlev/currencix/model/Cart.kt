package com.eliormachlev.currencix.model

/**
 * One line in a shopping cart: a display [name] and a raw calculator
 * [expression] like `"1.99"`, `"2 × 3.50"`, or `"10 + 5%"`. The expression
 * is stored verbatim so the user can re-edit it — evaluation happens at
 * display time via `String.evaluateCalculatorExpression()`.
 */
data class CartItem(
    val id: String,
    val name: String,
    val expression: String,
)

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
    // Which side of the conversion the fee stack is applied to. Persisted
    // per cart so each saved snapshot round-trips its own fee direction
    // through save/load and export/import, independent of any app-wide
    // default the user may have changed since.
    val feeSide: FeeSide = FeeSide.ORIGINAL,
)
