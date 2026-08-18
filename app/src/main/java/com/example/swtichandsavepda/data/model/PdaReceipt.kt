package com.example.swtichandsavepda.data.model

/**
 * One delivery booked against a purchase order.
 *
 * A PO can be received many times, so this is the audit trail: what arrived,
 * when, on whose delivery note, and whether the POS has accepted it.
 */
data class PurchaseOrderReceipt(
    val id: Long,
    val referenceNo: String?,
    val receivedAtEpochMs: Long?,
    val receivedBy: String?,
    val note: String?,
    val portalState: PortalState,
    val rejectReason: String?,
    val lines: List<ReceiptLine>,
) {
    val totalReceived: Double get() = lines.sumOf { it.quantityReceived }

    /** "GRN-20260814-001" when the supplier's note was captured, else "Receipt #31". */
    val title: String get() = referenceNo?.takeIf { it.isNotBlank() } ?: "Receipt #$id"
}

data class ReceiptLine(
    val productId: Long?,
    val productName: String?,
    val quantityReceived: Double,
    val selectedUnitCode: String?,
) {
    /** "24 BOX" / "24" */
    val quantityLabel: String
        get() = UomMath.pretty(quantityReceived) + (selectedUnitCode?.let { " $it" } ?: "")
}
