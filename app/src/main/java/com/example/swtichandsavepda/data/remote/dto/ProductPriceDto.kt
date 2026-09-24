package com.example.swtichandsavepda.data.remote.dto

import com.example.swtichandsavepda.data.remote.FlexibleDoubleSerializer
import kotlinx.serialization.Serializable

/** Body of `PUT /api/pda/products/{id}/price`. */
@Serializable
data class ProductPriceRequest(
    val retail: Double,
    val clientReference: String? = null,
)

/**
 * `data` of the price-update response. The portal sends the prices as decimal
 * strings ("2.49"), hence the flexible serializer.
 */
@Serializable
data class ProductPriceDto(
    val id: Long,
    val productName: String? = null,
    val barcode: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val previousRetail: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val retail: Double? = null,
    val isSynced: Boolean? = null,
)
