package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.CartItem
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
internal const val CART_KEY_EXTRAS = "extras"
internal const val CART_EXTRAS_KEY_TIP = "tipPercent"
internal const val CART_EXTRAS_KEY_SPLIT = "splitWays"
internal const val CART_EXTRAS_KEY_BUDGET = "budget"
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
        if (!cart.extras.isDefault) put(CART_KEY_EXTRAS, serializeExtras(cart.extras))
        put(
            CART_KEY_ITEMS,
            JSONArray().apply {
                cart.items.forEach { put(serializeCartItem(it)) }
            },
        )
    }

// Amounts as plain strings, so no precision is lost to a JSON double.
private fun serializeExtras(extras: CartExtras): JSONObject =
    JSONObject().apply {
        extras.tipPercent?.let { put(CART_EXTRAS_KEY_TIP, it.toPlainString()) }
        if (extras.splitWays > 1) put(CART_EXTRAS_KEY_SPLIT, extras.splitWays)
        extras.budget?.let { put(CART_EXTRAS_KEY_BUDGET, it.toPlainString()) }
    }

// Values out of range (a hand-edited or foreign file) fall back to "none".
private fun parseExtras(obj: JSONObject?): CartExtras {
    obj ?: return CartExtras()
    return CartExtras(
        tipPercent = obj.optString(CART_EXTRAS_KEY_TIP).toBigDecimalOrNull()?.takeIf { it.signum() > 0 },
        splitWays = obj.optInt(CART_EXTRAS_KEY_SPLIT, 1).takeIf { it in 1..CartExtras.MAX_SPLIT } ?: 1,
        budget = obj.optString(CART_EXTRAS_KEY_BUDGET).toBigDecimalOrNull()?.takeIf { it.signum() > 0 },
    )
}

private fun serializeCartItem(item: CartItem): JSONObject =
    JSONObject().apply {
        put(CART_ITEM_KEY_ID, item.id)
        put(CART_ITEM_KEY_NAME, item.name)
        put(CART_ITEM_KEY_EXPR, item.expression)
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
        extras = parseExtras(obj.optJSONObject(CART_KEY_EXTRAS)),
    )
}

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
