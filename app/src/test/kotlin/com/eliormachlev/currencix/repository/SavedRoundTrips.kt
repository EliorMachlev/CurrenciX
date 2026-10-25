package com.eliormachlev.currencix.repository

import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.RateAlert
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.proto.CartProto
import com.eliormachlev.currencix.proto.FeeProto
import com.eliormachlev.currencix.proto.RateAlertProto
import com.eliormachlev.currencix.proto.TripProto
import com.eliormachlev.currencix.repository.json.jsonText
import com.eliormachlev.currencix.repository.json.parseJsonObject

// Each saved value written and read back the two ways it's stored: as
// Protobuf bytes (the phone) and as JSON text (backups, cart files).

internal fun SavedCart.throughProto(): SavedCart = CartProto.ADAPTER.decode(toProto().encode()).toModel()

internal fun SavedCart.throughJson(): SavedCart? = parseCart(parseJsonObject(jsonText(serializeCart(this))))

internal fun Trip.throughProto(): Trip? = TripProto.ADAPTER.decode(toProto().encode()).toModel()

internal fun Trip.throughJson(): Trip? = parseTrip(parseJsonObject(jsonText(serializeTrip(this))))

internal fun Fee.throughProto(): Fee? = FeeProto.ADAPTER.decode(toProto().encode()).toModel()

internal fun Fee.throughJson(): Fee? = parseFee(parseJsonObject(jsonText(serializeFee(this))))

internal fun RateAlert.throughProto(): RateAlert? = RateAlertProto.ADAPTER.decode(toProto().encode()).toModel()

internal fun RateAlert.throughJson(): RateAlert? = parseRateAlert(parseJsonObject(jsonText(serializeRateAlert(this))))
