package com.example.swtichandsavepda.data.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A unit a product can be bought / returned / adjusted in (Multi-UOM, API doc §7).
 *
 * Stock is always tracked in **base** units; [conversionToBase] is how many base
 * units one of this unit contains (PCS → 1, BOX → 12, …). A product that the
 * portal has no unit rows for is "legacy / single-unit" and is represented by a
 * null unit throughout the app — the create payloads then omit every UOM field,
 * which the portal treats as conversion 1.
 */
data class ProductUnit(
    val productUnitId: Long,
    val selectedUnitId: Long?,
    val code: String,
    val name: String?,
    val barcode: String?,
    val conversionToBase: Double,
    val retailPrice: Double?,
    val purchaseCost: Double?,
    val isBaseUnit: Boolean,
    val allowDecimal: Boolean,
    val decimalPlaces: Int,
) {
    /** "Box" when the portal named the unit, otherwise its code ("BOX"). */
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: code

    /** "Box × 12" — what the unit chips show. */
    val labelWithFactor: String
        get() = if (isBase) label else "$label × ${UomMath.pretty(conversionToBase)}"

    /** True when one of this unit *is* one base unit, so no conversion applies. */
    val isBase: Boolean get() = isBaseUnit || conversionToBase == 1.0
}

/**
 * One row of `GET /api/pda/resolve-barcode` — the product a scanned barcode
 * belongs to *and* the unit that barcode identifies. A barcode can match more
 * than one row (the same code registered against two units), so the scanner asks
 * the operator which one they meant.
 */
data class BarcodeMatch(
    val productId: Long,
    val productName: String,
    val matchedBarcode: String?,
    val isPrimary: Boolean,
    val unit: ProductUnit,
)

/**
 * Selected-unit ⇄ base-unit arithmetic.
 *
 * Done in [BigDecimal] with explicit rounding rather than raw `Double` maths:
 * `0.1 * 3` in binary floating point is `0.30000000000000004`, and that value
 * would be sent as a stock quantity and a unit cost. The portal returns these
 * fields as 4-decimal strings ("10.0000"), so 4 is the scale we round to.
 */
object UomMath {

    private const val SCALE = 4

    /** entered quantity × conversion → the base-unit quantity the POS books. */
    fun baseQuantity(enteredQuantity: Double, conversionToBase: Double): Double {
        if (!isUsable(enteredQuantity, conversionToBase)) return enteredQuantity
        return BigDecimal.valueOf(enteredQuantity)
            .multiply(BigDecimal.valueOf(conversionToBase))
            .setScale(SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    /** entered unit cost ÷ conversion → the cost of one base unit. */
    fun baseUnitCost(enteredUnitCost: Double, conversionToBase: Double): Double {
        if (!isUsable(enteredUnitCost, conversionToBase)) return enteredUnitCost
        return BigDecimal.valueOf(enteredUnitCost)
            .divide(BigDecimal.valueOf(conversionToBase), SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * `BigDecimal.valueOf` throws `NumberFormatException` on NaN or infinity,
     * and that would escape `safeApiCall` (which catches IO/HTTP/serialization,
     * not arithmetic) and kill the coroutine. A portal sending `"1e400"` for a
     * conversion is exotic but must not crash a stock write, so fall back to
     * treating the value as already-base.
     */
    private fun isUsable(value: Double, conversionToBase: Double): Boolean =
        value.isFinite() && conversionToBase.isFinite() && conversionToBase > 0.0

    /** Drops the trailing ".0" so a conversion of 12.0 reads as "12". */
    fun pretty(value: Double): String =
        if (value % 1.0 == 0.0 && value.isFinite()) value.toLong().toString() else value.toString()
}
