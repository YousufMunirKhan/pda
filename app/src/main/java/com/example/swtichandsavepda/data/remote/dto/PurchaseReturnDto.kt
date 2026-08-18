package com.example.swtichandsavepda.data.remote.dto

import com.example.swtichandsavepda.data.remote.CompactDoubleSerializer
import com.example.swtichandsavepda.data.remote.FlexibleDoubleSerializer
import kotlinx.serialization.Serializable

/** Body for `POST /api/pda/purchase-returns` and `PUT …/{id}`. */
@Serializable
data class PurchaseReturnRequest(
    /** Idempotency key, unique per shop. See [StockAdjustmentRequest.clientReference]. */
    val clientReference: String? = null,
    val supplierId: Long,
    /**
     * The PO these goods arrived on. Optional — omitted for a plain
     * supplier-level return, which keeps working exactly as before.
     */
    val purchaseOrderId: Long? = null,
    val referenceNo: String,
    val returnReason: String,
    val items: List<PurchaseReturnItemRequest>,
)

/**
 * A return line. [quantity] and [costPrice] are in the **selected unit**; the
 * Multi-UOM block (API doc §7) is omitted for legacy / single-unit products.
 * Returns use `base_quantity` where purchase orders use `base_quantity_ordered`.
 */
@Serializable
data class PurchaseReturnItemRequest(
    val productId: Long,
    /**
     * Which PO line is being returned. A PO can carry two lines for the same
     * product at different costs, so product_id alone cannot identify one.
     */
    val purchaseOrderItemId: Long? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val quantity: Double,
    @Serializable(with = CompactDoubleSerializer::class)
    val costPrice: Double,
    val reason: String,
    val productUnitId: Long? = null,
    val selectedUnitId: Long? = null,
    val selectedUnitCode: String? = null,
    val selectedUnitName: String? = null,
    val selectedUnitBarcode: String? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val conversionFactor: Double? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val baseQuantity: Double? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val baseUnitCost: Double? = null,
)

@Serializable
data class PurchaseReturnDto(
    val id: Long,
    val supplierId: Long? = null,
    val purchaseOrderId: Long? = null,
    val purchaseOrderReference: String? = null,
    val clientReference: String? = null,
    val supplierName: String? = null,
    val referenceNo: String? = null,
    val returnReason: String? = null,
    val status: String? = null,
    val portalState: String? = null,
    val rejectReason: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val total: Double? = null,
    val items: List<PurchaseReturnItemDto>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PurchaseReturnItemDto(
    val id: Long? = null,
    val productId: Long? = null,
    val productName: String? = null,
    val sku: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val quantity: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val costPrice: Double? = null,
    val reason: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val lineTotal: Double? = null,
    // Multi-UOM echo — present only when the line was created with a unit.
    val selectedUnitCode: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val conversionFactor: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val baseQuantity: Double? = null,
)
