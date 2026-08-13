package com.example.swtichandsavepda.presentation.screens.adjuststock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.model.AdjustmentMode
import com.example.swtichandsavepda.data.model.NewStockAdjustment
import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.StockAdjustmentDoc
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.repository.PdaReferenceRepository
import com.example.swtichandsavepda.data.repository.PdaStockAdjustmentRepository
import com.example.swtichandsavepda.presentation.UnitChoice
import com.example.swtichandsavepda.presentation.components.ReferenceOption
import com.example.swtichandsavepda.presentation.components.toOption
import com.example.swtichandsavepda.presentation.loadUnitChoice
import com.example.swtichandsavepda.presentation.scannedProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdjustStockUiState(
    val mode: AdjustmentMode = AdjustmentMode.DECREASE,
    val product: ReferenceOption? = null,
    val unitChoice: UnitChoice = UnitChoice(),
    val quantity: String = "",
    val sourceLocation: ReferenceOption? = null,
    val destinationLocation: ReferenceOption? = null,
    val destinationShopId: String = "",
    val unitCost: String = "",
    val reason: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val successMessage: String? = null,
    val recent: List<StockAdjustmentDoc> = emptyList(),
    val isLoadingRecent: Boolean = false,
    val cancellingId: Long? = null,
) {
    val locationsClash: Boolean
        get() = mode == AdjustmentMode.LOCATION_MOVE &&
            sourceLocation != null &&
            destinationLocation != null &&
            sourceLocation.id == destinationLocation.id

    /** "= 60 Pcs" under the quantity field when a non-base unit is selected. */
    val baseQuantityHint: String? get() = unitChoice.baseQuantityHint(quantity)

    val canSubmit: Boolean
        get() = !isSubmitting &&
            product != null &&
            (quantity.toDoubleOrNull() ?: 0.0) > 0.0 &&
            extrasValid

    private val extrasValid: Boolean
        get() {
            if (mode.requiresSourceLocation && sourceLocation == null) return false
            if (mode.requiresDestinationLocation && destinationLocation == null) return false
            if (mode.requiresDestinationShop && destinationShopId.toLongOrNull() == null) return false
            if (mode.requiresUnitCost && (unitCost.toDoubleOrNull() ?: 0.0) <= 0.0) return false
            if (locationsClash) return false
            return true
        }
}

@HiltViewModel
class AdjustStockViewModel @Inject constructor(
    private val repository: PdaStockAdjustmentRepository,
    private val referenceRepository: PdaReferenceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdjustStockUiState())
    val uiState: StateFlow<AdjustStockUiState> = _uiState.asStateFlow()

    init {
        loadRecent()
        // Pre-select the product — and the unit the barcode resolved to — when we
        // arrive from a scan.
        savedStateHandle.scannedProduct()?.let { scanned ->
            selectProduct(scanned.option, scanned.productUnitId)
        }
    }

    suspend fun searchProducts(query: String): Result<List<ReferenceOption>> =
        referenceRepository.searchProducts(query).map { list -> list.map { it.toOption() } }

    suspend fun searchLocations(query: String): Result<List<ReferenceOption>> =
        referenceRepository.searchLocations(query).map { list -> list.map { it.toOption() } }

    fun selectMode(mode: AdjustmentMode) {
        _uiState.update { it.copy(mode = mode, error = null, fieldErrors = emptyMap()) }
    }

    fun selectProduct(option: ReferenceOption) = selectProduct(option, null)

    fun selectProduct(option: ReferenceOption, preferredProductUnitId: Long?) {
        _uiState.update {
            it.copy(
                product = option,
                // A new product invalidates the old product's units.
                unitChoice = UnitChoice(),
                // Prefill unit cost from the product when this mode needs one.
                unitCost = if (it.mode.requiresUnitCost && it.unitCost.isBlank()) {
                    option.cost?.let { cost -> "%.2f".format(cost) } ?: it.unitCost
                } else {
                    it.unitCost
                },
                error = null,
            )
        }
        loadUnits(option.id, preferredProductUnitId)
    }

    private fun loadUnits(productId: Long, preferredProductUnitId: Long?) {
        viewModelScope.launch {
            val choice = referenceRepository.loadUnitChoice(productId, preferredProductUnitId)
            // Ignore a late response for a product the operator has moved on from.
            _uiState.update { if (it.product?.id == productId) it.copy(unitChoice = choice) else it }
        }
    }

    fun selectUnit(unit: ProductUnit) {
        _uiState.update { state ->
            state.copy(
                unitChoice = state.unitChoice.copy(selected = unit),
                // The entered quantity meant the previous unit — clear it rather than
                // silently re-reading "5 Pcs" as "5 Box".
                quantity = "",
                unitCost = unit.purchaseCost?.let { "%.2f".format(it) } ?: state.unitCost,
                error = null,
            )
        }
    }

    fun selectSourceLocation(option: ReferenceOption) {
        _uiState.update { it.copy(sourceLocation = option, error = null) }
    }

    fun selectDestinationLocation(option: ReferenceOption) {
        _uiState.update { it.copy(destinationLocation = option, error = null) }
    }

    fun setQuantity(text: String) {
        _uiState.update {
            it.copy(quantity = sanitizeDecimal(text, it.unitChoice.quantityDecimals), error = null)
        }
    }

    fun setDestinationShop(text: String) {
        _uiState.update { it.copy(destinationShopId = text.filter(Char::isDigit).take(MAX_DIGITS), error = null) }
    }

    fun setUnitCost(text: String) {
        _uiState.update { it.copy(unitCost = sanitizeDecimal(text), error = null) }
    }

    fun setReason(text: String) {
        _uiState.update { it.copy(reason = text, error = null) }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(error = null, successMessage = null, fieldErrors = emptyMap()) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val productId = state.product?.id ?: return

        val adjustment = NewStockAdjustment(
            productId = productId,
            mode = state.mode,
            // Entered in the selected unit; the repository converts to base.
            quantity = state.quantity.toDoubleOrNull() ?: return,
            unit = state.unitChoice.unitForRequest,
            sourceLocationId = state.sourceLocation?.id.takeIf { state.mode.requiresSourceLocation },
            destinationLocationId = state.destinationLocation?.id
                .takeIf { state.mode.requiresDestinationLocation },
            destinationShopId = state.destinationShopId.toLongOrNull()
                .takeIf { state.mode.requiresDestinationShop },
            unitCost = state.unitCost.toDoubleOrNull().takeIf { state.mode.requiresUnitCost },
            reason = state.reason.trim().ifBlank { null },
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(isSubmitting = true, error = null, successMessage = null, fieldErrors = emptyMap())
            }
            repository.create(adjustment)
                .onSuccess { doc ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            product = null,
                            unitChoice = UnitChoice(),
                            quantity = "",
                            sourceLocation = null,
                            destinationLocation = null,
                            destinationShopId = "",
                            unitCost = "",
                            reason = "",
                            successMessage = "Adjustment #${doc.id} created — ${doc.portalState.pretty()}.",
                        )
                    }
                    loadRecent()
                }
                .onFailure { throwable -> _uiState.update { it.applyError(throwable) } }
        }
    }

    fun loadRecent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRecent = true) }
            repository.list()
                .onSuccess { docs ->
                    _uiState.update { it.copy(isLoadingRecent = false, recent = docs.take(RECENT_LIMIT)) }
                }
                .onFailure { _uiState.update { it.copy(isLoadingRecent = false) } }
        }
    }

    fun cancel(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(cancellingId = id, error = null) }
            repository.cancel(id)
                .onSuccess {
                    _uiState.update { it.copy(cancellingId = null, successMessage = "Adjustment #$id cancelled.") }
                    loadRecent()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(cancellingId = null).applyError(throwable) }
                }
        }
    }

    private fun AdjustStockUiState.applyError(throwable: Throwable): AdjustStockUiState = when (throwable) {
        is PdaApiException.Validation -> copy(
            isSubmitting = false,
            error = throwable.message,
            fieldErrors = throwable.fieldErrors,
        )

        is PdaApiException -> copy(isSubmitting = false, error = throwable.message)
        else -> copy(isSubmitting = false, error = "Something went wrong. Please try again.")
    }

    private companion object {
        const val MAX_DIGITS = 9
        const val RECENT_LIMIT = 20
    }
}

/**
 * Digits with at most one decimal point and [maxDecimals] decimal places.
 *
 * Quantity fields pass the selected unit's `decimal_places` — 0 for a whole-unit
 * product such as Pcs, which reduces this to a digits-only filter and keeps the
 * portal's "whole numbers for Pcs" rule un-typable rather than merely rejected.
 */
internal fun sanitizeDecimal(raw: String, maxDecimals: Int = 2): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    if (firstDot < 0) return filtered.take(MAX_WHOLE_DIGITS)
    val whole = filtered.substring(0, firstDot).take(MAX_WHOLE_DIGITS)
    if (maxDecimals <= 0) return whole
    val fraction = filtered.substring(firstDot + 1).filter(Char::isDigit).take(maxDecimals)
    return "$whole.$fraction"
}

private const val MAX_WHOLE_DIGITS = 9

internal fun PortalState.pretty(): String = when (this) {
    PortalState.PENDING -> "pending POS confirmation"
    PortalState.CONFIRMED -> "confirmed"
    PortalState.REJECTED -> "rejected"
    PortalState.UNKNOWN -> "draft"
}
