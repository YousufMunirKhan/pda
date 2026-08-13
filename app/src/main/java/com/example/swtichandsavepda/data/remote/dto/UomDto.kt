package com.example.swtichandsavepda.data.remote.dto

import com.example.swtichandsavepda.data.remote.FlexibleDoubleSerializer
import kotlinx.serialization.Serializable

/**
 * Row of `GET /api/pda/resolve-barcode?barcode=` — the product a barcode belongs
 * to plus the unit it identifies. `product_id` and `product_unit_id` are portal
 * ids and go straight into a create payload.
 */
@Serializable
data class BarcodeMatchDto(
    val productId: Long,
    val productName: String? = null,
    val productUnitId: Long? = null,
    val selectedUnitId: Long? = null,
    val selectedUnitCode: String? = null,
    val selectedUnitName: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val conversionToBase: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val retailPrice: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val purchaseCost: Double? = null,
    val allowDecimal: Boolean? = null,
    val decimalPlaces: Int? = null,
    val matchedBarcode: String? = null,
    val isPrimary: Boolean? = null,
)

/**
 * Row of `GET /api/pda/products/{id}/units` — the units a product can be
 * transacted in, for the unit selector.
 */
@Serializable
data class ProductUnitDto(
    val productUnitId: Long,
    val selectedUnitId: Long? = null,
    val selectedUnitCode: String? = null,
    val selectedUnitName: String? = null,
    val selectedUnitBarcode: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val conversionToBase: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val retailPrice: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val purchaseCost: Double? = null,
    val isBaseUnit: Boolean? = null,
    val allowDecimal: Boolean? = null,
    val decimalPlaces: Int? = null,
)
