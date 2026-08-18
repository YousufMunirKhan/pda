package com.example.swtichandsavepda.data.remote.dto

import com.example.swtichandsavepda.data.remote.CompactDoubleSerializer
import com.example.swtichandsavepda.data.remote.FlexibleDoubleSerializer
import kotlinx.serialization.Serializable

/**
 * Body for `POST /api/pda/stock-adjustments` and `PUT …/{id}`.
 *
 * One request covers every mode; which extras are required is set by
 * [adjustmentType] + [direction] (see the API doc's mode table). Optionals are
 * null by default so they are omitted from the payload when not applicable.
 *
 * Multi-UOM (API doc §7) differs here from purchase orders and returns: the
 * top-level [quantity] and [unitCost] are the **base** values, and the operator's
 * own entry rides along in [enteredQuantity] / [enteredUnitCost] with the
 * conversion. For a single-unit product entered == base and the whole
 * `productUnitId … baseUnitCost` block is omitted.
 */
@Serializable
data class StockAdjustmentRequest(
    /**
     * Idempotency key, unique per shop. A repeat of the same reference returns
     * the document already created rather than a second one, which turns an
     * ambiguous write into an exact lookup instead of a heuristic match.
     */
    val clientReference: String? = null,
    val productId: Long,
    val adjustmentType: String,
    val direction: String,
    @Serializable(with = CompactDoubleSerializer::class)
    val quantity: Double,
    val sourceLocationId: Long? = null,
    val destinationLocationId: Long? = null,
    val destinationShopId: Long? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val unitCost: Double? = null,
    val reason: String? = null,
    val productUnitId: Long? = null,
    val selectedUnitId: Long? = null,
    val selectedUnitCode: String? = null,
    val selectedUnitName: String? = null,
    val selectedUnitBarcode: String? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val enteredQuantity: Double? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val conversionToBase: Double? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val baseQuantity: Double? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val enteredUnitCost: Double? = null,
    @Serializable(with = CompactDoubleSerializer::class)
    val baseUnitCost: Double? = null,
)

@Serializable
data class StockAdjustmentDto(
    val id: Long,
    val productId: Long? = null,
    val productName: String? = null,
    val sku: String? = null,
    val adjustmentType: String? = null,
    val direction: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val quantity: Double? = null,
    val sourceLocationId: Long? = null,
    val destinationLocationId: Long? = null,
    val destinationShopId: Long? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val unitCost: Double? = null,
    val reason: String? = null,
    val status: String? = null,
    val portalState: String? = null,
    val rejectReason: String? = null,
    /** Echo of the idempotency key we sent — the exact reconciliation handle. */
    val clientReference: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    // Multi-UOM echo — present only when the draft was created with a unit.
    val selectedUnitCode: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val enteredQuantity: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val conversionToBase: Double? = null,
)
