package com.example.swtichandsavepda.data.remote.dto

import com.example.swtichandsavepda.data.remote.CompactDoubleSerializer
import com.example.swtichandsavepda.data.remote.FlexibleDoubleSerializer
import kotlinx.serialization.Serializable

/** Body for `POST /api/pda/purchase-orders` and `PUT …/{id}`. */
@Serializable
data class PurchaseOrderRequest(
    val supplierId: Long,
    val expectedDeliveryDate: String,
    val items: List<PurchaseOrderItemRequest>,
)

/**
 * A PO line. [quantityOrdered] and [unitCost] are in the **selected unit**; the
 * `productUnitId … baseUnitCost` block is the optional Multi-UOM detail (API doc
 * §7) and is omitted wholesale for legacy / single-unit products, which the
 * portal then treats as conversion 1.
 */
@Serializable
data class PurchaseOrderItemRequest(
    val productId: Long,
    @Serializable(with = CompactDoubleSerializer::class)
    val quantityOrdered: Double,
    @Serializable(with = CompactDoubleSerializer::class)
    val unitCost: Double,
    val productUnitId: Long? = null,
    val selectedUnitId: Long? = null,
    val selectedUnitCode: String? = null,
    val selectedUnitName: String? = null,
    val selectedUnitBarcode: String? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val conversionFactor: Double? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val baseQuantityOrdered: Double? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val baseUnitCost: Double? = null,
)

/**
 * Body for `POST …/{id}/receive`. Omit [items] entirely to receive everything;
 * otherwise name the lines and quantities that arrived.
 */
@Serializable
data class ReceivePurchaseOrderRequest(
    val items: List<ReceiveItemRequest>? = null,
)

@Serializable
data class ReceiveItemRequest(
    val productId: Long,
    @Serializable(with = CompactDoubleSerializer::class)
    val quantityReceived: Double,
)

@Serializable
data class PurchaseOrderDto(
    val id: Long,
    val supplierId: Long? = null,
    val supplierName: String? = null,
    val reference: String? = null,
    val referenceNo: String? = null,
    val expectedDeliveryDate: String? = null,
    val status: String? = null,
    val portalState: String? = null,
    val rejectReason: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val subtotal: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val total: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val totalAmount: Double? = null,
    val items: List<PurchaseOrderItemDto>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PurchaseOrderItemDto(
    val id: Long? = null,
    val productId: Long? = null,
    val productName: String? = null,
    val sku: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val quantityOrdered: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val quantityReceived: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val unitCost: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val lineTotal: Double? = null,
    // Multi-UOM echo — present only when the line was created with a unit.
    val selectedUnitCode: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val conversionFactor: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val baseQuantityOrdered: Double? = null,
)
