package com.example.swtichandsavepda.presentation

import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.data.repository.PdaReferenceRepository

/**
 * The unit picker's state for the product currently on a form (Multi-UOM, API
 * doc §7). Shared by every create screen so the four ViewModels don't each
 * re-implement "which unit is the operator entering in, and what is that in base
 * units".
 *
 * An empty [units] list is the legacy / single-unit case: nothing is shown, no
 * UOM fields are sent, and the conversion is 1.
 */
data class UnitChoice(
    val units: List<ProductUnit> = emptyList(),
    val selected: ProductUnit? = null,
) {
    /** Only worth showing a selector when the product really has alternatives. */
    val hasChoice: Boolean get() = units.size > 1

    val conversion: Double get() = selected?.conversionToBase ?: 1.0

    /**
     * The unit to attach to the created line. Null for a single-unit product, so
     * the request omits the UOM block entirely.
     */
    val unitForRequest: ProductUnit? get() = selected?.takeIf { units.isNotEmpty() }

    /** How many decimals the quantity field accepts — 0 unless the unit allows them. */
    val quantityDecimals: Int
        get() = if (selected?.allowDecimal == true) selected.decimalPlaces.coerceIn(0, MAX_DECIMALS) else 0

    /** The base unit's label ("Pcs"), for the "= 60 Pcs" hint. */
    private val baseUnitLabel: String?
        get() = units.firstOrNull { it.isBase }?.label

    /**
     * "= 60 Pcs" under the quantity field, so the operator can see what the POS
     * will actually book. Null when there is no conversion to explain.
     */
    fun baseQuantityHint(enteredText: String): String? {
        if (conversion == 1.0) return null
        val entered = enteredText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        val base = UomMath.pretty(UomMath.baseQuantity(entered, conversion))
        return "= $base ${baseUnitLabel ?: "base units"}"
    }

    private companion object {
        const val MAX_DECIMALS = 4
    }
}

/**
 * Loads the units for a product and preselects one: the [preferredProductUnitId]
 * a scan resolved to, otherwise the base unit, otherwise the first row.
 *
 * A product with no unit rows — or a lookup that fails — yields an empty
 * [UnitChoice] and the form behaves exactly as it did before Multi-UOM. Unit
 * data is convenience, never a reason to block a stock movement.
 */
suspend fun PdaReferenceRepository.loadUnitChoice(
    productId: Long,
    preferredProductUnitId: Long? = null,
): UnitChoice = productUnits(productId).fold(
    onSuccess = { units ->
        UnitChoice(
            units = units,
            selected = units.firstOrNull { it.productUnitId == preferredProductUnitId }
                ?: units.firstOrNull { it.isBase }
                ?: units.firstOrNull(),
        )
    },
    onFailure = { UnitChoice() },
)
