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
 * [status] exists because "this product has one unit" and "we do not yet know
 * what units this product has" must not look the same. Conflating them omits the
 * UOM block from the payload, the portal applies conversion 1, and an entered
 * "5" meaning five boxes is booked as five pieces — a silent 12× error with no
 * symptom on screen. A quantity may only be sent once [isResolved].
 */
data class UnitChoice(
    val units: List<ProductUnit> = emptyList(),
    val selected: ProductUnit? = null,
    val status: Status = Status.Resolved,
) {
    enum class Status {
        /** The units lookup is in flight. What a typed quantity means is unknown. */
        Loading,

        /** The portal answered. An empty [units] here is a genuine single-unit product. */
        Resolved,

        /** The lookup failed. Offer a retry; do not guess at conversion 1. */
        Failed,
    }

    /** Only worth showing a selector when the product really has alternatives. */
    val hasChoice: Boolean get() = units.size > 1

    val conversion: Double get() = selected?.conversionToBase ?: 1.0

    /**
     * True only when we know what the entered quantity means. Gates submission —
     * see the class comment for why this is not merely cosmetic.
     */
    val isResolved: Boolean get() = status == Status.Resolved

    /**
     * The unit to attach to the created line. Null for a single-unit product, so
     * the request omits the UOM block.
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

    /**
     * "= £2.0000 per Pcs" under the cost field. The portal books stock at the
     * base cost, so an operator entering a per-Box price should be able to see
     * the per-piece figure it becomes.
     */
    fun baseCostHint(enteredText: String): String? {
        if (conversion == 1.0) return null
        val entered = enteredText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        val base = UomMath.baseUnitCost(entered, conversion)
        return "= £${"%.4f".format(base)} per ${baseUnitLabel ?: "base unit"}"
    }

    private companion object {
        const val MAX_DECIMALS = 4
    }
}

/**
 * Loads the units for a product and preselects one: the [preferredProductUnitId]
 * a scan resolved to, otherwise the base unit, otherwise the first row.
 *
 * A failed lookup yields [UnitChoice.Status.Failed] rather than an empty choice.
 * The earlier behaviour — degrading silently to "no units" — made a network blip
 * indistinguishable from a single-unit product, which is how an entered quantity
 * ends up booked in the wrong unit.
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
            status = UnitChoice.Status.Resolved,
        )
    },
    onFailure = { UnitChoice(status = UnitChoice.Status.Failed) },
)
