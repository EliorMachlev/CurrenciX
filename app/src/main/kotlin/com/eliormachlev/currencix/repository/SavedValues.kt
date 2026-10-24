package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeType
import com.eliormachlev.currencix.model.Person
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.util.newId
import java.math.BigDecimal

// The saved values rebuilt from their stored fields, whichever format held
// them (the Protobuf files, a JSON backup or cart file): each read applies
// the same checks, so a hand-edited or foreign file can't put a value the
// app doesn't expect into it. A value out of range falls back to "none".

/** [id], or a new one when it's empty (an entry saved without one). */
internal fun idOrNew(id: String): String = id.ifEmpty { newId() }

/** The text as a decimal above zero, or null (missing, malformed, zero or negative). */
internal fun positiveDecimalOrNull(text: String?): BigDecimal? = text?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

/** The item as stored, checked: an id, a quantity in range, an empty person as none. */
internal fun CartItem.checked(): CartItem =
    copy(
        id = idOrNew(id),
        quantity = quantity.coerceIn(CartItem.MIN_QUANTITY, CartItem.MAX_QUANTITY),
        personId = personId?.ifEmpty { null },
    )

internal fun personOf(
    id: String,
    name: String,
): Person = Person(idOrNew(id), name)

internal fun cartExtrasOf(
    tipPercent: String?,
    splitWays: Int,
    budget: String?,
    people: List<Person>,
): CartExtras =
    CartExtras(
        tipPercent = positiveDecimalOrNull(tipPercent),
        splitWays = splitWays.takeIf { it in 1..CartExtras.MAX_SPLIT } ?: 1,
        budget = positiveDecimalOrNull(budget),
        people = people.take(CartExtras.MAX_PEOPLE),
    )

/** A trip, or null when its currency isn't one the app knows. */
internal fun tripOf(
    id: String,
    name: String,
    currencyCode: String,
    budget: String?,
): Trip? {
    val currency = Currency.fromString(currencyCode) ?: return null
    return Trip(idOrNew(id), name, currency, positiveDecimalOrNull(budget))
}

/** The fields of a fee, as stored. */
internal class StoredFee(
    val id: String,
    val name: String,
    val percent: String,
    val isActive: Boolean,
    val type: FeeType?,
    val pair: StoredPair = StoredPair(),
)

/** A specific-pair fee's currencies, and whether it applies both ways (empty for other fees). */
internal class StoredPair(
    val from: String = "",
    val to: String = "",
    val bothWays: Boolean = false,
)

/** The fee these fields make, or null when they can't be one (no valid percent, unknown type). */
internal fun StoredFee.toFee(): Fee? {
    val id = idOrNew(id)
    val percent = percent.toBigDecimalOrNull() ?: return null
    return when (type) {
        FeeType.GLOBAL_EXCHANGE -> Fee.GlobalExchange(id, name, percent, isActive)
        FeeType.GLOBAL_BANK -> Fee.GlobalBank(id, name, percent, isActive)
        FeeType.SPECIFIC_PAIR -> Fee.SpecificPair(id, name, percent, pair.from, pair.to, pair.bothWays, isActive)
        null -> null
    }
}

/** [this] fee's fields, to store. */
internal fun Fee.toStored(): StoredFee =
    StoredFee(
        id = id,
        name = name,
        percent = percent.toPlainString(),
        isActive = isActive,
        type = type,
        pair = (this as? Fee.SpecificPair)?.let { StoredPair(it.from, it.to, it.bothWays) } ?: StoredPair(),
    )
