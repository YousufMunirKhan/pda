package com.example.swtichandsavepda.presentation

import androidx.lifecycle.SavedStateHandle
import com.example.swtichandsavepda.presentation.components.ReferenceOption

/** Nav-arg keys shared by the scanner routes and the feature ViewModels. */
object ProductArgs {
    const val ID = "productId"
    const val NAME = "productName"
    const val COST = "productCost"

    /** Base retail price, so the price editor opens on the current figure. */
    const val RETAIL = "productRetail"

    /**
     * The unit `resolve-barcode` matched, so the destination form opens on the
     * unit that was actually scanned (a BOX label pre-selects Box, not Pcs).
     */
    const val UNIT_ID = "productUnitId"
}

/**
 * What a scan handed to this screen: the product to pre-select and, for a
 * Multi-UOM barcode, the unit it resolved to.
 */
data class ScannedProductArgs(
    val option: ReferenceOption,
    val productUnitId: Long?,
)

/**
 * The scanned product, or null when the screen was opened from the menu.
 * Navigation URL-decodes string args, so the name is read back as-is.
 */
fun SavedStateHandle.scannedProduct(): ScannedProductArgs? {
    val id = get<String>(ProductArgs.ID)?.toLongOrNull() ?: return null
    val name = get<String>(ProductArgs.NAME)?.takeIf { it.isNotBlank() } ?: "Product #$id"
    val cost = get<String>(ProductArgs.COST)?.toDoubleOrNull()
    val retail = get<String>(ProductArgs.RETAIL)?.toDoubleOrNull()
    return ScannedProductArgs(
        option = ReferenceOption(id = id, title = name, cost = cost, retail = retail),
        productUnitId = get<String>(ProductArgs.UNIT_ID)?.toLongOrNull(),
    )
}
