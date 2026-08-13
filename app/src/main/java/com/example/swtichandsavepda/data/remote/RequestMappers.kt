package com.example.swtichandsavepda.data.remote

import com.example.swtichandsavepda.data.model.NewPurchaseOrderLine
import com.example.swtichandsavepda.data.model.NewPurchaseReturnLine
import com.example.swtichandsavepda.data.model.NewStockAdjustment
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.data.remote.dto.PurchaseOrderItemRequest
import com.example.swtichandsavepda.data.remote.dto.PurchaseReturnItemRequest
import com.example.swtichandsavepda.data.remote.dto.StockAdjustmentRequest

/**
 * Domain → wire mappers (the mirror of [Mappers.kt]'s wire → domain direction).
 *
 * The selected-unit → base-unit conversion lives here, in one place, rather than
 * in each repository or ViewModel: getting it wrong writes the wrong quantity
 * into stock, and it is worth having exactly one implementation under test.
 *
 * A line whose `unit` is null is a legacy / single-unit product: every Multi-UOM
 * field is omitted and the portal treats the conversion as 1.
 */

/** Entered values stay on `quantity_ordered` / `unit_cost`; base rides alongside. */
internal fun NewPurchaseOrderLine.toRequest() = PurchaseOrderItemRequest(
    productId = productId,
    quantityOrdered = quantityOrdered,
    unitCost = unitCost,
    productUnitId = unit?.productUnitId,
    selectedUnitId = unit?.selectedUnitId,
    selectedUnitCode = unit?.code,
    selectedUnitName = unit?.name,
    selectedUnitBarcode = unit?.barcode,
    conversionFactor = unit?.conversionToBase,
    baseQuantityOrdered = unit?.let { UomMath.baseQuantity(quantityOrdered, it.conversionToBase) },
    baseUnitCost = unit?.let { UomMath.baseUnitCost(unitCost, it.conversionToBase) },
)

/** Same contract as a PO line, but the base quantity key is `base_quantity`. */
internal fun NewPurchaseReturnLine.toRequest() = PurchaseReturnItemRequest(
    productId = productId,
    quantity = quantity,
    costPrice = costPrice,
    reason = reason,
    productUnitId = unit?.productUnitId,
    selectedUnitId = unit?.selectedUnitId,
    selectedUnitCode = unit?.code,
    selectedUnitName = unit?.name,
    selectedUnitBarcode = unit?.barcode,
    conversionFactor = unit?.conversionToBase,
    baseQuantity = unit?.let { UomMath.baseQuantity(quantity, it.conversionToBase) },
    baseUnitCost = unit?.let { UomMath.baseUnitCost(costPrice, it.conversionToBase) },
)

/**
 * Stock adjustments differ from the document lines: the top-level `quantity` and
 * `unit_cost` are the **base** values the POS books, and the operator's own entry
 * is carried separately in `entered_quantity` / `entered_unit_cost`.
 */
internal fun NewStockAdjustment.toRequest(): StockAdjustmentRequest {
    val conversion = unit?.conversionToBase ?: 1.0
    val baseQuantity = UomMath.baseQuantity(quantity, conversion)
    val baseUnitCost = unitCost?.let { UomMath.baseUnitCost(it, conversion) }

    return StockAdjustmentRequest(
        productId = productId,
        adjustmentType = mode.adjustmentType,
        direction = mode.direction,
        quantity = baseQuantity,
        sourceLocationId = sourceLocationId,
        destinationLocationId = destinationLocationId,
        destinationShopId = destinationShopId,
        unitCost = baseUnitCost,
        reason = reason,
        productUnitId = unit?.productUnitId,
        selectedUnitId = unit?.selectedUnitId,
        selectedUnitCode = unit?.code,
        selectedUnitName = unit?.name,
        selectedUnitBarcode = unit?.barcode,
        enteredQuantity = unit?.let { quantity },
        conversionToBase = unit?.conversionToBase,
        baseQuantity = unit?.let { baseQuantity },
        enteredUnitCost = unit?.let { unitCost },
        baseUnitCost = unit?.let { baseUnitCost },
    )
}
