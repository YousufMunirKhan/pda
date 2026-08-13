package com.example.swtichandsavepda.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reads a numeric field the portal may send either as a JSON number (`1.25`) or
 * a quoted string (`"10.0000"`) — the portal returns quantities and money as
 * decimal strings. This is a non-null serializer; applied to a `Double?`
 * property the compiler plugin wraps it as nullable, so a JSON `null` still
 * decodes to `null` without reaching here.
 */
object FlexibleDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive
        return primitive?.contentOrNull?.trim()?.toDoubleOrNull() ?: 0.0
    }

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }
}

/**
 * Reads like [FlexibleDoubleSerializer] but writes a **compact** number: a whole
 * value goes over the wire as an integer (`24`, not `24.0`), a fractional one
 * keeps its decimals (`2.5`).
 *
 * Quantities are `Double` in the app because a Multi-UOM unit may allow decimals
 * (see [com.example.swtichandsavepda.data.model.ProductUnit.allowDecimal]), but
 * most are whole and the portal validates several of these fields as integers —
 * `"quantity_ordered":24.0` would be rejected where `24` is accepted. Money uses
 * it too: `1.25` stays `1.25`, and a round `2.0` is sent as `2`.
 */
object CompactDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CompactDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double = FlexibleDoubleSerializer.deserialize(decoder)

    override fun serialize(encoder: Encoder, value: Double) {
        if (value.isFinite() && value % 1.0 == 0.0 && value in SAFE_INTEGRAL_RANGE) {
            encoder.encodeLong(value.toLong())
        } else {
            encoder.encodeDouble(value)
        }
    }

    /** Beyond 2^53 a Double can no longer represent every integer exactly. */
    private val SAFE_INTEGRAL_RANGE = -9_007_199_254_740_992.0..9_007_199_254_740_992.0
}
