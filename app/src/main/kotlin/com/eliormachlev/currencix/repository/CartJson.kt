package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.FeeSide
import com.eliormachlev.currencix.model.SavedCart
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

// Wire keys for on-disk cart JSON. Kept top-level (not nested inside
// [Database]) so [CartExporter] can share the same serde for the file
// envelope's `cart` payload.
internal const val CART_KEY_ID = "id"
internal const val CART_KEY_NAME = "name"
internal const val CART_KEY_CURRENCY = "currency"
internal const val CART_KEY_DEST_CURRENCY = "destinationCurrency"
internal const val CART_KEY_ITEMS = "items"
internal const val CART_KEY_CREATED_AT = "createdAt"
internal const val CART_KEY_FEE_SIDE = "feeSide"
internal const val CART_ITEM_KEY_ID = "id"
internal const val CART_ITEM_KEY_NAME = "name"
internal const val CART_ITEM_KEY_EXPR = "expression"
internal const val CART_ITEM_KEY_PINNED = "pinned"

internal fun serializeCart(cart: SavedCart): JSONObject =
    JSONObject().apply {
        put(CART_KEY_ID, cart.id)
        put(CART_KEY_NAME, cart.name)
        put(CART_KEY_CURRENCY, cart.currency)
        cart.destinationCurrency?.let { put(CART_KEY_DEST_CURRENCY, it) }
        put(CART_KEY_CREATED_AT, cart.createdAt)
        put(CART_KEY_FEE_SIDE, cart.feeSide.name)
        put(
            CART_KEY_ITEMS,
            JSONArray().apply {
                cart.items.forEach { put(serializeCartItem(it)) }
            },
        )
    }

private fun serializeCartItem(item: CartItem): JSONObject =
    JSONObject().apply {
        put(CART_ITEM_KEY_ID, item.id)
        put(CART_ITEM_KEY_NAME, item.name)
        put(CART_ITEM_KEY_EXPR, item.expression)
        // Only write the pin flag when set — keeps legacy-shape parity for
        // unpinned rows and shrinks the on-disk payload for typical carts.
        if (item.pinned) put(CART_ITEM_KEY_PINNED, true)
    }

internal fun parseCart(obj: JSONObject?): SavedCart? {
    obj ?: return null
    val itemsArr = obj.optJSONArray(CART_KEY_ITEMS) ?: JSONArray()
    val items =
        (0 until itemsArr.length()).mapNotNull { i ->
            parseCartItem(itemsArr.optJSONObject(i))
        }
    return SavedCart(
        id = obj.optString(CART_KEY_ID, "").ifEmpty { UUID.randomUUID().toString() },
        name = obj.optString(CART_KEY_NAME, ""),
        currency = obj.optString(CART_KEY_CURRENCY, ""),
        destinationCurrency = obj.optString(CART_KEY_DEST_CURRENCY, "").ifEmpty { null },
        items = items,
        createdAt = obj.optLong(CART_KEY_CREATED_AT, System.currentTimeMillis()),
        // Legacy payloads (pre-per-cart fee side) fall back to ORIGINAL so
        // they render the same way the app used to render them.
        feeSide = parseFeeSideOrDefault(obj.optString(CART_KEY_FEE_SIDE, "")),
    )
}

private fun parseFeeSideOrDefault(raw: String): FeeSide = runCatching { FeeSide.valueOf(raw) }.getOrDefault(FeeSide.ORIGINAL)

internal fun parseCart(json: String?): SavedCart? {
    if (json.isNullOrBlank()) return null
    return try {
        parseCart(JSONObject(json))
    } catch (e: JSONException) {
        null
    }
}

private fun parseCartItem(obj: JSONObject?): CartItem? {
    obj ?: return null
    return CartItem(
        id = obj.optString(CART_ITEM_KEY_ID, "").ifEmpty { UUID.randomUUID().toString() },
        name = obj.optString(CART_ITEM_KEY_NAME, ""),
        expression = obj.optString(CART_ITEM_KEY_EXPR, ""),
        // Legacy payloads pre-pin default to false (matches the class-level
        // default) so an old cart on disk round-trips as unpinned.
        pinned = obj.optBoolean(CART_ITEM_KEY_PINNED, false),
    )
}

internal fun serializeCartList(list: List<SavedCart>): String {
    val arr = JSONArray()
    list.forEach { arr.put(serializeCart(it)) }
    return arr.toString()
}

internal fun parseCartList(json: String?): List<SavedCart> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i -> parseCart(arr.optJSONObject(i)) }
    } catch (e: JSONException) {
        emptyList()
    }
}
