package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Person
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.repository.json.JsonObject
import com.eliormachlev.currencix.repository.json.MalformedJsonException
import com.eliormachlev.currencix.repository.json.buildJsonObject
import com.eliormachlev.currencix.repository.json.jsonText
import com.eliormachlev.currencix.repository.json.parseJsonObject
import timber.log.Timber

// Wire keys for on-disk cart JSON. Kept top-level (not nested inside
// [Database]) so [CartExporter] can share the same serde for the file
// envelope's `cart` payload.
private const val TAG = "CartJson"

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
internal const val CART_ITEM_KEY_QUANTITY = "quantity"
internal const val CART_ITEM_KEY_CHECKED = "checked"
internal const val CART_KEY_SHOPPING_MODE = "shoppingMode"
internal const val CART_ITEM_KEY_PERSON = "person"
internal const val CART_KEY_TRIP = "trip"
internal const val CART_EXTRAS_KEY_PEOPLE = "people"
internal const val PERSON_KEY_ID = "id"
internal const val PERSON_KEY_NAME = "name"

internal fun serializeCart(cart: SavedCart): Map<String, Any> =
    buildJsonObject {
        put(CART_KEY_ID, cart.id)
        put(CART_KEY_NAME, cart.name)
        put(CART_KEY_CURRENCY, cart.currency)
        cart.destinationCurrency?.let { put(CART_KEY_DEST_CURRENCY, it) }
        put(CART_KEY_CREATED_AT, cart.createdAt)
        if (cart.shoppingMode) put(CART_KEY_SHOPPING_MODE, true)
        cart.tripId?.let { put(CART_KEY_TRIP, it) }
        if (!cart.extras.isDefault) put(CART_KEY_EXTRAS, serializeExtras(cart.extras))
        put(CART_KEY_ITEMS, cart.items.map(::serializeCartItem))
    }

// Amounts as plain strings, so no precision is lost to a JSON double.
private fun serializeExtras(extras: CartExtras): Map<String, Any> =
    buildJsonObject {
        extras.tipPercent?.let { put(CART_EXTRAS_KEY_TIP, it.toPlainString()) }
        if (extras.splitWays > 1) put(CART_EXTRAS_KEY_SPLIT, extras.splitWays)
        extras.budget?.let { put(CART_EXTRAS_KEY_BUDGET, it.toPlainString()) }
        if (extras.people.isNotEmpty()) {
            put(CART_EXTRAS_KEY_PEOPLE, extras.people.map { mapOf(PERSON_KEY_ID to it.id, PERSON_KEY_NAME to it.name) })
        }
    }

// Values out of range (a hand-edited or foreign file) fall back to "none".
private fun parseExtras(obj: JsonObject?): CartExtras {
    obj ?: return CartExtras()
    return CartExtras(
        tipPercent = obj.optString(CART_EXTRAS_KEY_TIP).toBigDecimalOrNull()?.takeIf { it.signum() > 0 },
        splitWays = obj.optInt(CART_EXTRAS_KEY_SPLIT, 1).takeIf { it in 1..CartExtras.MAX_SPLIT } ?: 1,
        budget = obj.optString(CART_EXTRAS_KEY_BUDGET).toBigDecimalOrNull()?.takeIf { it.signum() > 0 },
        people =
            obj
                .optObjects(CART_EXTRAS_KEY_PEOPLE)
                .map { Person(it.optIdOrNew(PERSON_KEY_ID), it.optString(PERSON_KEY_NAME, "")) }
                .take(CartExtras.MAX_PEOPLE),
    )
}

private fun serializeCartItem(item: CartItem): Map<String, Any> =
    buildJsonObject {
        put(CART_ITEM_KEY_ID, item.id)
        put(CART_ITEM_KEY_NAME, item.name)
        put(CART_ITEM_KEY_EXPR, item.expression)
        if (item.pinned) put(CART_ITEM_KEY_PINNED, true)
        if (item.quantity > 1) put(CART_ITEM_KEY_QUANTITY, item.quantity)
        if (item.checked) put(CART_ITEM_KEY_CHECKED, true)
        item.personId?.let { put(CART_ITEM_KEY_PERSON, it) }
    }

internal fun parseCart(obj: JsonObject?): SavedCart? {
    obj ?: return null
    val items = obj.optObjects(CART_KEY_ITEMS).map(::parseCartItem)
    return SavedCart(
        id = obj.optIdOrNew(CART_KEY_ID),
        name = obj.optString(CART_KEY_NAME, ""),
        currency = obj.optString(CART_KEY_CURRENCY, ""),
        destinationCurrency = obj.optStringOrNull(CART_KEY_DEST_CURRENCY),
        items = items,
        createdAt = obj.optLong(CART_KEY_CREATED_AT, System.currentTimeMillis()),
        extras = parseExtras(obj.optObject(CART_KEY_EXTRAS)),
        shoppingMode = obj.optBoolean(CART_KEY_SHOPPING_MODE, false),
        tripId = obj.optStringOrNull(CART_KEY_TRIP),
    )
}

internal fun parseCart(json: String?): SavedCart? {
    if (json.isNullOrBlank()) return null
    return try {
        parseCart(parseJsonObject(json))
    } catch (e: MalformedJsonException) {
        Timber.tag(TAG).w(e, "Malformed saved cart, dropping it")
        null
    }
}

private fun parseCartItem(obj: JsonObject): CartItem =
    CartItem(
        id = obj.optIdOrNew(CART_ITEM_KEY_ID),
        name = obj.optString(CART_ITEM_KEY_NAME, ""),
        expression = obj.optString(CART_ITEM_KEY_EXPR, ""),
        pinned = obj.optBoolean(CART_ITEM_KEY_PINNED, false),
        quantity = obj.optInt(CART_ITEM_KEY_QUANTITY, 1).coerceIn(CartItem.MIN_QUANTITY, CartItem.MAX_QUANTITY),
        checked = obj.optBoolean(CART_ITEM_KEY_CHECKED, false),
        personId = obj.optStringOrNull(CART_ITEM_KEY_PERSON),
    )

/** The cart as JSON text, as one preference holds it. */
internal fun serializeCartText(cart: SavedCart): String = jsonText(serializeCart(cart))

internal fun serializeCartList(list: List<SavedCart>): String = list.toJsonArrayText(::serializeCart)

internal fun parseCartList(json: String?): List<SavedCart> = parseJsonList(json, TAG, "saved-cart", ::parseCart)
