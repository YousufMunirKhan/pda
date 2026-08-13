package com.example.swtichandsavepda.data.model

/**
 * Domain-side inputs for the create/edit calls, so ViewModels never touch the
 * wire DTOs. Repositories translate these into request bodies — including the
 * selected-unit → base-unit conversion, which is done in exactly one place
 * (see [UomMath]) rather than in each ViewModel.
 *
 * Quantities and costs here are always what the **operator entered, in [unit]**.
 * A null [unit] is a legacy / single-unit product: no UOM fields are sent and
 * the portal treats the conversion as 1.
 */

data class NewPurchaseOrderLine(
    val productId: Long,
    val quantityOrdered: Double,
    val unitCost: Double,
    val unit: ProductUnit? = null,
)

data class NewPurchaseReturnLine(
    val productId: Long,
    val quantity: Double,
    val costPrice: Double,
    val reason: String,
    val unit: ProductUnit? = null,
)

/**
 * The five stock-adjustment modes. Each carries its wire [adjustmentType] +
 * [direction] and declares which extra fields the API requires, so the UI can
 * show exactly the fields that mode needs.
 */
enum class AdjustmentMode(
    val label: String,
    val adjustmentType: String,
    val direction: String,
    val requiresSourceLocation: Boolean = false,
    val requiresDestinationLocation: Boolean = false,
    val requiresDestinationShop: Boolean = false,
    val requiresUnitCost: Boolean = false,
) {
    DECREASE(
        label = "Decrease / Damaged / Wastage",
        adjustmentType = "Stock Decrease",
        direction = "OUT",
        requiresSourceLocation = true,
    ),
    INCREASE(
        label = "Increase",
        adjustmentType = "Stock Increase",
        direction = "IN",
        requiresUnitCost = true,
    ),
    LOCATION_MOVE(
        label = "Location move",
        adjustmentType = "Location Move",
        direction = "MOVE",
        requiresSourceLocation = true,
        requiresDestinationLocation = true,
    ),
    BRANCH_TRANSFER_OUT(
        label = "Branch transfer out",
        adjustmentType = "Branch Transfer Out",
        direction = "TRANSFER",
        requiresDestinationShop = true,
    ),
    OPENING_STOCK(
        label = "Opening stock",
        adjustmentType = "Opening Stock",
        direction = "IN",
        requiresUnitCost = true,
    ),
}

data class NewStockAdjustment(
    val productId: Long,
    val mode: AdjustmentMode,
    val quantity: Double,
    val sourceLocationId: Long? = null,
    val destinationLocationId: Long? = null,
    val destinationShopId: Long? = null,
    val unitCost: Double? = null,
    val reason: String? = null,
    val unit: ProductUnit? = null,
)
