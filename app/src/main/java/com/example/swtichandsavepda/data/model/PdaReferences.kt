package com.example.swtichandsavepda.data.model

/** A product from the portal pick list / barcode lookup. */
data class ProductRef(
    val id: Long,
    val name: String,
    val barcode: String?,
    val code: String?,
    val cost: Double?,
    val retail: Double?,
    val unitType: String?,
)

/** A supplier from the portal pick list. */
data class SupplierRef(
    val id: Long,
    val name: String,
    val phone: String?,
    val email: String?,
)

/** A stock location from the portal pick list. */
data class StockLocationRef(
    val id: Long,
    val code: String?,
    val name: String,
    val type: String?,
    val isDefault: Boolean,
)
