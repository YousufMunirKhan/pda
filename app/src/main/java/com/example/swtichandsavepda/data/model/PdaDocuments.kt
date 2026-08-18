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
    /** When the portal created it — bounds an ambiguous-write reconciliation. */
    val createdAtEpochMs: Long? = null,
    /** Echo of the idempotency key, when the portal supports one. */
    val clientReference: String? = null,
    /** Portal-computed totals; null falls back to summing the lines. */
    private val portalTotals: PurchaseOrderTotals? = null,
) {
    val orderedTotal: Double
        get() = portalTotals?.ordered ?: lines.sumOf { it.quantityOrdered }
    val receivedTotal: Double
        get() = portalTotals?.received ?: lines.sumOf { it.quantityReceived ?: 0.0 }
    val returnedTotal: Double
        get() = portalTotals?.returned ?: lines.sumOf { it.quantityReturned ?: 0.0 }

    /** What is still outstanding across the whole PO. Never negative. */
    val remainingTotal: Double
        get() = (portalTotals?.remaining ?: lines.sumOf { it.quantityRemaining }).coerceAtLeast(0.0)

    /**
     * True once nothing is outstanding.
     *
     * Derived from the line quantities rather than from [status], because a
     * partially-received PO reports a status the app should not have to
     * string-match to understand ("Partially Received", "Pending", …).
     */
    val isFullyReceived: Boolean get() = lines.isNotEmpty() && remainingTotal <= 0.0

    /** A receipt has happened but the PO is still open. */
    val isPartiallyReceived: Boolean get() = receivedTotal > 0.0 && !isFullyReceived

    val isCancelled: Boolean get() = status?.contains("cancel", ignoreCase = true) == true

    /** Only an open, uncancelled PO with something outstanding can be received. */
    val canReceive: Boolean get() = !isCancelled && !isFullyReceived && lines.isNotEmpty()
}

/**
 * The portal's own PO totals. Preferred over summing lines so the two cannot
 * disagree — the portal is the authority on what it holds.
 */
data class PurchaseOrderTotals(
    val ordered: Double?,
    val received: Double?,
    val returned: Double?,
    val remaining: Double?,
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
    /** Total returned against this line. Null until the portal reports it. */
    val quantityReturned: Double? = null,
    /** The portal's own remaining figure, when it sends one. */
    val reportedRemaining: Double? = null,
) {
    /**
     * What is still outstanding on this line.
     *
     * Uses the portal's figure when present. Floored at zero either way: if more
     * is reported received than ordered, the honest reading is "nothing left to
     * receive", not a negative that would reach a receive form as a limit.
     */
    val quantityRemaining: Double
        get() = (reportedRemaining ?: (quantityOrdered - (quantityReceived ?: 0.0)))
            .coerceAtLeast(0.0)

    val isFullyReceived: Boolean get() = quantityRemaining <= 0.0
}

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
    /** When the portal created it — bounds an ambiguous-write reconciliation. */
    val createdAtEpochMs: Long? = null,
    /** Echo of the idempotency key — the exact reconciliation handle. */
    val clientReference: String? = null,
    /** The PO these goods came in on, when the return was raised against one. */
    val purchaseOrderId: Long? = null,
    val purchaseOrderReference: String? = null,
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
    /** When the portal created it — bounds an ambiguous-write reconciliation. */
    val createdAtEpochMs: Long? = null,
    /** Echo of the idempotency key — the exact reconciliation handle. */
    val clientReference: String? = null,
)
