package com.example.swtichandsavepda.presentation

import com.example.swtichandsavepda.data.model.PurchaseOrderReceipt

/**
 * The receiving history sheet's state for one purchase order.
 *
 * Held separately from the PO list because it is fetched on demand: a warehouse
 * scrolling twenty orders should not pull twenty histories it will never open.
 */
data class ReceiptHistory(
    val orderId: Long,
    val orderReference: String,
    /** Printed on the GRN slip; the receipts themselves do not carry it. */
    val supplierName: String? = null,
    val receipts: List<PurchaseOrderReceipt> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
