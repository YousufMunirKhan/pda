package com.example.swtichandsavepda.data.model

/**
 * The lifecycle every PDA document carries. Drafts start [PENDING]; the POS then
 * moves them to [CONFIRMED] or [REJECTED] (with a reason). [UNKNOWN] guards
 * against a state string we have not seen.
 */
enum class PortalState {
    PENDING,
    CONFIRMED,
    REJECTED,
    UNKNOWN;

    companion object {
        fun from(raw: String?): PortalState = when (raw?.trim()?.lowercase()) {
            "pending" -> PENDING
            "confirmed" -> CONFIRMED
            "rejected" -> REJECTED
            else -> UNKNOWN
        }
    }
}

data class PurchaseOrderDoc(
    val id: Long,
    val reference: String,
    val supplierId: Long?,
    val supplierName: String?,
    val expectedDeliveryDate: String?,
    val status: String?,
    val portalState: PortalState,
    val rejectReason: String?,
    val total: Double?,
    val lines: List<PurchaseOrderDocLine>,
)

/**
 * Quantities are [Double] rather than [Int]: a Multi-UOM unit may allow decimals
 * (`allow_decimal`), and the portal returns them as 4-decimal strings.
 * [selectedUnitCode] is set only when the line was created against a unit.
 */
data class PurchaseOrderDocLine(
    val productId: Long?,
    val productName: String?,
    val quantityOrdered: Double,
    val quantityReceived: Double?,
    val unitCost: Double,
    val selectedUnitCode: String? = null,
)

data class PurchaseReturnDoc(
    val id: Long,
    val referenceNo: String,
    val supplierId: Long?,
    val supplierName: String?,
    val returnReason: String?,
    val portalState: PortalState,
    val rejectReason: String?,
    val total: Double?,
    val lines: List<PurchaseReturnDocLine>,
)

data class PurchaseReturnDocLine(
    val productId: Long?,
    val productName: String?,
    val quantity: Double,
    val costPrice: Double,
    val reason: String?,
    val selectedUnitCode: String? = null,
)

data class StockAdjustmentDoc(
    val id: Long,
    val productId: Long?,
    val productName: String?,
    val adjustmentType: String?,
    val direction: String?,
    /** The **base**-unit quantity the POS will book. */
    val quantity: Double,
    val unitCost: Double?,
    val reason: String?,
    val portalState: PortalState,
    val rejectReason: String?,
    /** What the operator typed, when a non-base unit was used. */
    val enteredQuantity: Double? = null,
    val selectedUnitCode: String? = null,
)
