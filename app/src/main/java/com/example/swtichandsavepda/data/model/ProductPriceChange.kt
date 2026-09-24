package com.example.swtichandsavepda.data.model

/**
 * A base retail price the portal has accepted. The POS picks it up on its next
 * product sync, so the till may show the old price for a short while.
 */
data class ProductPriceChange(
    val productId: Long,
    val productName: String?,
    val barcode: String?,
    val previousRetail: Double?,
    val retail: Double,
)
