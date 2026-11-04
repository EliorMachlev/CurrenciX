package com.eliormachlev.currencix.model.provider.api

import com.eliormachlev.currencix.model.adapter.decimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import java.math.BigDecimal
import java.time.LocalDate

/**
 * How every provider's JSON is read: fields the app doesn't use are passed
 * over, so a provider adding one changes nothing.
 */
internal val PROVIDER_JSON: Json = Json { ignoreUnknownKeys = true }

/** A rate, read from a JSON number or string exactly as written: no digit goes through a double. */
internal typealias Decimal =
    @Serializable(with = DecimalSerializer::class)
    BigDecimal

/** An ISO date ("2026-10-06"). */
internal typealias IsoDate =
    @Serializable(with = IsoDateSerializer::class)
    LocalDate

internal object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CurrenciX.Decimal", PrimitiveKind.STRING)

    // A number's own text when reading JSON; a string otherwise.
    override fun deserialize(decoder: Decoder): BigDecimal =
        (decoder as? JsonDecoder)?.decodeJsonElement()?.decimal() ?: BigDecimal(decoder.decodeString())

    override fun serialize(
        encoder: Encoder,
        value: BigDecimal,
    ) = encoder.encodeString(value.toPlainString())
}

internal object IsoDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CurrenciX.IsoDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())

    override fun serialize(
        encoder: Encoder,
        value: LocalDate,
    ) = encoder.encodeString(value.toString())
}
