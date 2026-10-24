package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.AlertDirection
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeType
import com.eliormachlev.currencix.model.RateAlert
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.proto.CartItemProto
import com.eliormachlev.currencix.proto.CartProto
import com.eliormachlev.currencix.proto.FeeProto
import com.eliormachlev.currencix.proto.PersonProto
import com.eliormachlev.currencix.proto.RateAlertProto
import com.eliormachlev.currencix.proto.TripProto
import java.time.LocalDate

// The saved carts, trips, fees and rate alerts to and from their Protobuf messages
// (src/main/proto/currencix/saved.proto). Reads go through the same checks as
// a JSON file's (SavedValues.kt).

internal fun SavedCart.toProto(): CartProto =
    CartProto(
        id = id,
        name = name,
        currency = currency,
        destination_currency = destinationCurrency,
        items = items.map { it.toProto() },
        created_at = createdAt,
        shopping_mode = shoppingMode,
        trip_id = tripId,
        tip_percent = extras.tipPercent?.toPlainString(),
        split_ways = extras.splitWays,
        budget = extras.budget?.toPlainString(),
        people = extras.people.map { PersonProto(id = it.id, name = it.name) },
    )

internal fun CartProto.toModel(): SavedCart =
    SavedCart(
        id = idOrNew(id),
        name = name,
        currency = currency,
        destinationCurrency = destination_currency?.ifEmpty { null },
        items = items.map { it.toModel() },
        createdAt = created_at,
        extras = cartExtrasOf(tip_percent, split_ways, budget, people.map { personOf(it.id, it.name) }),
        shoppingMode = shopping_mode,
        tripId = trip_id?.ifEmpty { null },
    )

private fun CartItem.toProto(): CartItemProto =
    CartItemProto(
        id = id,
        name = name,
        expression = expression,
        pinned = pinned,
        quantity = quantity,
        checked = checked,
        person_id = personId,
    )

private fun CartItemProto.toModel(): CartItem = CartItem(id, name, expression, pinned, quantity, checked, person_id).checked()

internal fun Trip.toProto(): TripProto =
    TripProto(
        id = id,
        name = name,
        currency = currency.iso4217Alpha(),
        budget = budget?.toPlainString(),
        destination = destination?.iso4217Alpha(),
        start_day = start?.toEpochDay(),
        end_day = end?.toEpochDay(),
    )

/** The trip, or null when its currency isn't one the app knows. */
internal fun TripProto.toModel(): Trip? =
    tripOf(id, name, currency, budget)?.withPlan(destination, start_day?.let(LocalDate::ofEpochDay), end_day?.let(LocalDate::ofEpochDay))

internal fun Fee.toProto(): FeeProto {
    val stored = toStored()
    return FeeProto(
        id = stored.id,
        name = stored.name,
        percent = stored.percent,
        active = stored.isActive,
        kind = type.toKind(),
        from = stored.pair.from,
        to = stored.pair.to,
        both_ways = stored.pair.bothWays,
    )
}

/** The fee, or null when the message can't be one (no valid percent, unknown kind). */
internal fun FeeProto.toModel(): Fee? = StoredFee(id, name, percent, active, kind.toType(), StoredPair(from, to, both_ways)).toFee()

private fun FeeType.toKind(): FeeProto.Kind =
    when (this) {
        FeeType.GLOBAL_EXCHANGE -> FeeProto.Kind.GLOBAL_EXCHANGE
        FeeType.GLOBAL_BANK -> FeeProto.Kind.GLOBAL_BANK
        FeeType.SPECIFIC_PAIR -> FeeProto.Kind.SPECIFIC_PAIR
    }

private fun FeeProto.Kind.toType(): FeeType? =
    when (this) {
        FeeProto.Kind.GLOBAL_EXCHANGE -> FeeType.GLOBAL_EXCHANGE
        FeeProto.Kind.GLOBAL_BANK -> FeeType.GLOBAL_BANK
        FeeProto.Kind.SPECIFIC_PAIR -> FeeType.SPECIFIC_PAIR
        FeeProto.Kind.KIND_UNSPECIFIED -> null
    }

internal fun RateAlert.toProto(): RateAlertProto =
    RateAlertProto(
        id = id,
        from = pair.from.iso4217Alpha(),
        to = pair.to.iso4217Alpha(),
        threshold = threshold.toPlainString(),
        direction =
            when (direction) {
                AlertDirection.ABOVE -> RateAlertProto.Direction.ABOVE
                AlertDirection.BELOW -> RateAlertProto.Direction.BELOW
            },
    )

/** The alert, or null when the message can't be one (an unknown currency or direction, no threshold). */
internal fun RateAlertProto.toModel(): RateAlert? {
    val direction =
        when (direction) {
            RateAlertProto.Direction.ABOVE -> AlertDirection.ABOVE
            RateAlertProto.Direction.BELOW -> AlertDirection.BELOW
            RateAlertProto.Direction.DIRECTION_UNSPECIFIED -> null
        }
    return rateAlertOf(id, from, to, threshold, direction)
}
