package com.example.swtichandsavepda.printer.model

import com.example.swtichandsavepda.data.model.PortalState

/**
 * Something the operator can print, independent of how it reaches paper.
 *
 * These carry display-ready values only: the renderer lays them out, and the
 * transport sends the bytes. Neither needs to know about the portal models.
 */
sealed interface PrintDocument {

    /** A sticker for the shelf or the product: name, price and a scannable barcode. */
    data class ProductLabel(
        val productName: String,
        val barcode: String,
        val price: Double?,
        /** "Box × 12" for a non-base unit, so a box label is not mistaken for a single. */
        val unitLabel: String? = null,
    ) : PrintDocument

    /** Goods Received Note for one delivery booked against a purchase order. */
    data class GoodsReceivedSlip(
        val storeName: String?,
        val receiptReference: String,
        val orderReference: String,
        val supplierName: String?,
        val receivedAtEpochMs: Long?,
        val receivedBy: String?,
        val note: String?,
        val status: PortalState,
        val lines: List<SlipLine>,
        val printedAtEpochMs: Long,
    ) : PrintDocument

    /** The paperwork that travels with goods going back to a supplier. */
    data class SupplierReturnSlip(
        val storeName: String?,
        val referenceNo: String,
        val supplierName: String?,
        val orderReference: String?,
        val reason: String?,
        val status: PortalState,
        val lines: List<SlipLine>,
        val total: Double,
        val printedAtEpochMs: Long,
    ) : PrintDocument

    /** Confirms the printer is reachable and can print a barcode. */
    data class TestPage(
        val connectionLabel: String,
        val printedAtEpochMs: Long,
    ) : PrintDocument
}

/**
 * One row of a slip. [unitPrice] is set on a return, where the value going back
 * matters; a GRN only records quantities.
 */
data class SlipLine(
    val name: String,
    val quantity: Double,
    val unitCode: String?,
    val unitPrice: Double? = null,
) {
    val lineTotal: Double? get() = unitPrice?.let { it * quantity }
}
