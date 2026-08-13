package com.example.swtichandsavepda.data.remote

import com.example.swtichandsavepda.data.model.AuthUser
import com.example.swtichandsavepda.data.model.BarcodeMatch
import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.PurchaseOrderDoc
import com.example.swtichandsavepda.data.model.PurchaseOrderDocLine
import com.example.swtichandsavepda.data.model.PurchaseReturnDoc
import com.example.swtichandsavepda.data.model.PurchaseReturnDocLine
import com.example.swtichandsavepda.data.model.ProductRef
import com.example.swtichandsavepda.data.model.StockAdjustmentDoc
import com.example.swtichandsavepda.data.model.StockLocationRef
import com.example.swtichandsavepda.data.model.SupplierRef
import com.example.swtichandsavepda.data.model.Tenant
import com.example.swtichandsavepda.data.remote.dto.BarcodeMatchDto
import com.example.swtichandsavepda.data.remote.dto.PdaUserDto
import com.example.swtichandsavepda.data.remote.dto.ProductRefDto
import com.example.swtichandsavepda.data.remote.dto.ProductUnitDto
import com.example.swtichandsavepda.data.remote.dto.PurchaseOrderDto
import com.example.swtichandsavepda.data.remote.dto.PurchaseReturnDto
import com.example.swtichandsavepda.data.remote.dto.StockAdjustmentDto
import com.example.swtichandsavepda.data.remote.dto.StockLocationRefDto
import com.example.swtichandsavepda.data.remote.dto.SupplierRefDto
import com.example.swtichandsavepda.data.remote.dto.TenantDto

/** Wire → domain mappers. Keep the UI independent of the DTO shapes. */

fun PdaUserDto.toDomain(): AuthUser = AuthUser(
    id = id,
    name = name,
    email = email,
    shopId = shopId,
)

fun TenantDto.toDomain(): Tenant = Tenant(id = id, name = name)

fun PurchaseOrderDto.toDomain(): PurchaseOrderDoc = PurchaseOrderDoc(
    id = id,
    reference = reference ?: referenceNo ?: "PO #$id",
    supplierId = supplierId,
    supplierName = supplierName,
    expectedDeliveryDate = expectedDeliveryDate,
    status = status,
    portalState = PortalState.from(portalState),
    rejectReason = rejectReason,
    total = totalAmount ?: total ?: subtotal,
    lines = items.orEmpty().map { line ->
        PurchaseOrderDocLine(
            productId = line.productId,
            productName = line.productName,
            quantityOrdered = line.quantityOrdered ?: 0.0,
            quantityReceived = line.quantityReceived,
            unitCost = line.unitCost ?: 0.0,
            selectedUnitCode = line.selectedUnitCode,
        )
    },
)

fun PurchaseReturnDto.toDomain(): PurchaseReturnDoc = PurchaseReturnDoc(
    id = id,
    referenceNo = referenceNo ?: "PR #$id",
    supplierId = supplierId,
    supplierName = supplierName,
    returnReason = returnReason,
    portalState = PortalState.from(portalState),
    rejectReason = rejectReason,
    total = total,
    lines = items.orEmpty().map { line ->
        PurchaseReturnDocLine(
            productId = line.productId,
            productName = line.productName,
            quantity = line.quantity ?: 0.0,
            costPrice = line.costPrice ?: 0.0,
            reason = line.reason,
            selectedUnitCode = line.selectedUnitCode,
        )
    },
)

fun ProductRefDto.toDomain(): ProductRef = ProductRef(
    id = id,
    name = productName ?: productCode ?: "Product #$id",
    barcode = barcode,
    code = productCode,
    cost = cost,
    retail = retail,
    unitType = unitType,
)

/**
 * A `resolve-barcode` row is flat — the product and its unit in one object — so
 * it splits into a [BarcodeMatch] wrapping a [ProductUnit]. A row without a
 * `product_unit_id` is a single-unit product; it still yields a base unit so the
 * scanner has something to show and pass on.
 */
fun BarcodeMatchDto.toDomain(): BarcodeMatch = BarcodeMatch(
    productId = productId,
    productName = productName ?: "Product #$productId",
    matchedBarcode = matchedBarcode,
    isPrimary = isPrimary ?: false,
    unit = ProductUnit(
        productUnitId = productUnitId ?: 0L,
        selectedUnitId = selectedUnitId,
        code = selectedUnitCode ?: selectedUnitName ?: "UNIT",
        name = selectedUnitName,
        barcode = matchedBarcode,
        conversionToBase = conversionToBase.orBaseConversion(),
        retailPrice = retailPrice,
        purchaseCost = purchaseCost,
        isBaseUnit = conversionToBase.orBaseConversion() == 1.0,
        allowDecimal = allowDecimal ?: false,
        decimalPlaces = decimalPlaces ?: 0,
    ),
)

fun ProductUnitDto.toDomain(): ProductUnit = ProductUnit(
    productUnitId = productUnitId,
    selectedUnitId = selectedUnitId,
    code = selectedUnitCode ?: selectedUnitName ?: "UNIT",
    name = selectedUnitName,
    barcode = selectedUnitBarcode,
    conversionToBase = conversionToBase.orBaseConversion(),
    retailPrice = retailPrice,
    purchaseCost = purchaseCost,
    isBaseUnit = isBaseUnit ?: (conversionToBase.orBaseConversion() == 1.0),
    allowDecimal = allowDecimal ?: false,
    decimalPlaces = decimalPlaces ?: 0,
)

/**
 * A missing or non-positive conversion means "one of this unit is one base unit".
 * Never let a 0 through — it would make every base quantity 0 (and, dividing a
 * cost, blow up).
 */
private fun Double?.orBaseConversion(): Double = this?.takeIf { it > 0.0 } ?: 1.0

fun SupplierRefDto.toDomain(): SupplierRef = SupplierRef(
    id = id,
    name = supplierName ?: "Supplier #$id",
    phone = phoneNumber,
    email = email,
)

fun StockLocationRefDto.toDomain(): StockLocationRef = StockLocationRef(
    id = id,
    code = locationCode,
    name = locationName ?: locationCode ?: "Location #$id",
    type = locationType,
    isDefault = isDefault ?: false,
)

fun StockAdjustmentDto.toDomain(): StockAdjustmentDoc = StockAdjustmentDoc(
    id = id,
    productId = productId,
    productName = productName,
    adjustmentType = adjustmentType,
    direction = direction,
    quantity = quantity ?: 0.0,
    unitCost = unitCost,
    reason = reason,
    portalState = PortalState.from(portalState),
    rejectReason = rejectReason,
    enteredQuantity = enteredQuantity,
    selectedUnitCode = selectedUnitCode,
)
