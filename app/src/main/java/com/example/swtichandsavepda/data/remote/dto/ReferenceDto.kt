package com.example.swtichandsavepda.data.remote.dto

import com.example.swtichandsavepda.data.remote.FlexibleDoubleSerializer
import kotlinx.serialization.Serializable

/** Row of `GET /api/pda/products` (barcode / name / code search + scan match). */
@Serializable
data class ProductRefDto(
    val id: Long,
    val productName: String? = null,
    val barcode: String? = null,
    val productCode: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val cost: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val retail: Double? = null,
    val unitType: String? = null,
)

/** Row of `GET /api/pda/suppliers`. */
@Serializable
data class SupplierRefDto(
    val id: Long,
    val supplierName: String? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
)

/** Row of `GET /api/pda/stock-locations`. */
@Serializable
data class StockLocationRefDto(
    val id: Long,
    val locationCode: String? = null,
    val locationName: String? = null,
    val locationType: String? = null,
    val isDefault: Boolean? = null,
)
