package com.example.swtichandsavepda.data.model

/**
 * A purchase-order line and what can still be returned against it.
 *
 * **[quantityReturnable] is a guide, not a guarantee.** The portal counts only
 * portal- and PDA-created returns in [quantityReturned], so a return raised on
 * the POS is invisible here and this figure can read higher than the truth.
 * That is why [quantityReceived] and [quantityReturned] are carried alongside:
 * an operator who knows a POS return happened can see the portal's returned
 * figure is zero and distrust the limit.
 */
data class ReturnableLine(
    val purchaseOrderItemId: Long?,
    val productId: Long,
    val productName: String,
    val unitCost: Double,
    val selectedUnitCode: String?,
    val quantityReceived: Double,
    val quantityReturned: Double,
    val quantityReturnable: Double,
) {
    val canReturn: Boolean get() = quantityReturnable > 0.0

    /** "4 returnable · 5 received · 1 already returned" */
    val summary: String
        get() = buildString {
            append("${UomMath.pretty(quantityReturnable)} returnable")
            append(" · ${UomMath.pretty(quantityReceived)} received")
            if (quantityReturned > 0.0) {
                append(" · ${UomMath.pretty(quantityReturned)} already returned")
            }
            selectedUnitCode?.let { append(" · $it") }
        }
}
