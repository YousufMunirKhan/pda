package com.example.swtichandsavepda.data.remote.dto

import com.example.swtichandsavepda.data.remote.FlexibleDoubleSerializer
import kotlinx.serialization.Serializable

/**
 * One goods-received note from `GET /api/pda/purchase-orders/{id}/receipts` —
 * a single delivery against the order, newest first.
 */
@Serializable
data class ReceiptDto(
    val id: Long,
    val purchaseOrderId: Long? = null,
    val referenceNo: String? = null,
    val receivedAt: String? = null,
    val receivedBy: String? = null,
    val note: String? = null,
    val portalState: String? = null,
    val rejectReason: String? = null,
    val clientReference: String? = null,
    val items: List<ReceiptItemDto>? = null,
)

@Serializable
data class ReceiptItemDto(
    val productId: Long? = null,
    val productName: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val quantityReceived: Double? = null,
    val selectedUnitCode: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val conversionToBase: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val baseQuantityReceived: Double? = null,
)
