package com.example.swtichandsavepda.data.remote.dto

import com.example.swtichandsavepda.data.remote.FlexibleDoubleSerializer
import kotlinx.serialization.Serializable

/**
 * A row of `GET /api/pda/purchase-orders/{id}/returnable` — one PO line and how
 * much of it can still be sent back.
 *
 * `quantity_returnable` is the portal's own figure (received − returned), so the
 * app never derives it from two other calls and drifts out of step.
 */
@Serializable
data class ReturnableLineDto(
    val purchaseOrderItemId: Long? = null,
    val productId: Long,
    val productName: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val unitCost: Double? = null,
    val selectedUnitCode: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val conversionToBase: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val quantityReceived: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val quantityReturned: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val quantityReturnable: Double? = null,
)
