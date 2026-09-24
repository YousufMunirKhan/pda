package com.example.swtichandsavepda.printer

import com.example.swtichandsavepda.data.model.ProductRef
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.PurchaseOrderReceipt
import com.example.swtichandsavepda.data.model.PurchaseReturnDoc
import com.example.swtichandsavepda.printer.model.PrintDocument
import com.example.swtichandsavepda.printer.model.SlipLine

/** Portal records → the printable documents. Kept here so no screen formats paper. */
object PrintDocuments {

    /**
     * The label carries the barcode that was scanned (a Box label keeps the
     * Box barcode) and, for a non-base unit, what that unit holds.
     */
    fun productLabel(product: ProductRef, unit: ProductUnit?): PrintDocument.ProductLabel? {
        val barcode = product.barcode?.takeIf { it.isNotBlank() }
            ?: unit?.barcode?.takeIf { it.isNotBlank() }
            ?: product.code?.takeIf { it.isNotBlank() }
            ?: return null
        return PrintDocument.ProductLabel(
            productName = product.name,
            barcode = barcode,
            price = product.retail,
            unitLabel = unit?.takeIf { !it.isBase }?.labelWithFactor,
        )
    }

    fun goodsReceived(
        receipt: PurchaseOrderReceipt,
        orderReference: String,
        supplierName: String?,
        storeName: String?,
        printedAtEpochMs: Long,
    ) = PrintDocument.GoodsReceivedSlip(
        storeName = storeName,
        receiptReference = receipt.title,
        orderReference = orderReference,
        supplierName = supplierName,
        receivedAtEpochMs = receipt.receivedAtEpochMs,
        receivedBy = receipt.receivedBy,
        note = receipt.note,
        status = receipt.portalState,
        lines = receipt.lines.map { line ->
            SlipLine(
                name = line.productName ?: "Product ${line.productId ?: "?"}",
                quantity = line.quantityReceived,
                unitCode = line.selectedUnitCode,
            )
        },
        printedAtEpochMs = printedAtEpochMs,
    )

    fun supplierReturn(
        doc: PurchaseReturnDoc,
        storeName: String?,
        printedAtEpochMs: Long,
    ): PrintDocument.SupplierReturnSlip {
        val lines = doc.lines.map { line ->
            SlipLine(
                name = line.productName ?: "Product ${line.productId ?: "?"}",
                quantity = line.quantity,
                unitCode = line.selectedUnitCode,
                unitPrice = line.costPrice,
            )
        }
        return PrintDocument.SupplierReturnSlip(
            storeName = storeName,
            referenceNo = doc.referenceNo,
            supplierName = doc.supplierName,
            orderReference = doc.purchaseOrderReference,
            reason = doc.returnReason,
            status = doc.portalState,
            lines = lines,
            // The portal's figure when it sends one — it is the authority.
            total = doc.total ?: lines.sumOf { it.lineTotal ?: 0.0 },
            printedAtEpochMs = printedAtEpochMs,
        )
    }
}
