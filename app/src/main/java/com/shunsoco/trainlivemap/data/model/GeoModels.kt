package com.shunsoco.trainlivemap.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * GeoJSON coordinate in `[longitude, latitude]` order.
 *
 * A data class is used instead of exposing a raw two-element list so callers
 * cannot accidentally swap latitude and longitude. The custom serializer
 * keeps the wire format identical to the Web API.
 */
@Serializable(with = LngLatSerializer::class)
data class LngLat(
    val longitude: Double,
    val latitude: Double,
)

object LngLatSerializer : KSerializer<LngLat> {
    private val delegate = ListSerializer(Double.serializer())

    override val descriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: LngLat,
    ) {
        delegate.serialize(
            encoder,
            listOf(value.longitude, value.latitude),
        )
    }

    override fun deserialize(decoder: Decoder): LngLat {
        val values = delegate.deserialize(decoder)
        require(values.size == COORDINATE_SIZE) {
            "LngLat must contain exactly two numbers in [longitude, latitude] order"
        }
        return LngLat(
            longitude = values[0],
            latitude = values[1],
        )
    }

    private const val COORDINATE_SIZE = 2
}
