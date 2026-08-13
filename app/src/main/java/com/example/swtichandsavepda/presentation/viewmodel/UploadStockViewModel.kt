package com.example.swtichandsavepda.presentation.screens.uploadstock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.model.AdjustmentMode
import com.example.swtichandsavepda.data.model.NewStockAdjustment
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.repository.PdaReferenceRepository
import com.example.swtichandsavepda.data.repository.PdaStockAdjustmentRepository
import com.example.swtichandsavepda.presentation.UnitChoice
import com.example.swtichandsavepda.presentation.components.ReferenceOption
import com.example.swtichandsavepda.presentation.components.toOption
import com.example.swtichandsavepda.presentation.loadUnitChoice
import com.example.swtichandsavepda.presentation.scannedProduct
import com.example.swtichandsavepda.presentation.screens.adjuststock.pretty
import com.example.swtichandsavepda.presentation.screens.adjuststock.sanitizeDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Upload new stock" is the fast goods-in path: a single [AdjustmentMode.INCREASE]
 * stock adjustment (product, quantity, unit cost). The POS performs the
 * authoritative FIFO receipt when it confirms the draft.
 */
data class UploadStockUiState(
    val product: ReferenceOption? = null,
    val unitChoice: UnitChoice = UnitChoice(),
    val quantity: String = "",
    val unitCost: String = "",
    val reason: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val successMessage: String? = null,
) {
    val baseQuantityHint: String? get() = unitChoice.baseQuantityHint(quantity)

    val canSubmit: Boolean
        get() = !isSubmitting &&
            product != null &&
            (quantity.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (unitCost.toDoubleOrNull() ?: 0.0) > 0.0
}

@HiltViewModel
class UploadStockViewModel @Inject constructor(
    private val repository: PdaStockAdjustmentRepository,
    private val referenceRepository: PdaReferenceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadStockUiState())
    val uiState: StateFlow<UploadStockUiState> = _uiState.asStateFlow()

    init {
        // Pre-select the product — and the scanned unit — when we arrive from a scan.
        savedStateHandle.scannedProduct()?.let { scanned ->
            selectProduct(scanned.option, scanned.productUnitId)
        }
    }

    suspend fun searchProducts(query: String): Result<List<ReferenceOption>> =
        referenceRepository.searchProducts(query).map { list -> list.map { it.toOption() } }

    fun selectProduct(option: ReferenceOption) = selectProduct(option, null)

    fun selectProduct(option: ReferenceOption, preferredProductUnitId: Long?) {
        _uiState.update {
            it.copy(
                product = option,
                unitChoice = UnitChoice(),
                unitCost = if (it.unitCost.isBlank()) {
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
            _uiState.update { if (it.product?.id == productId) it.copy(unitChoice = choice) else it }
        }
    }

    fun selectUnit(unit: ProductUnit) {
        _uiState.update { state ->
            state.copy(
                unitChoice = state.unitChoice.copy(selected = unit),
                quantity = "",
                unitCost = unit.purchaseCost?.let { "%.2f".format(it) } ?: state.unitCost,
                error = null,
            )
        }
    }

    fun setQuantity(text: String) {
        _uiState.update {
            it.copy(quantity = sanitizeDecimal(text, it.unitChoice.quantityDecimals), error = null)
        }
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
            mode = AdjustmentMode.INCREASE,
            quantity = state.quantity.toDoubleOrNull() ?: return,
            unit = state.unitChoice.unitForRequest,
            unitCost = state.unitCost.toDoubleOrNull(),
            reason = state.reason.trim().ifBlank { "Goods in (PDA)" },
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(isSubmitting = true, error = null, successMessage = null, fieldErrors = emptyMap())
            }
            repository.create(adjustment)
                .onSuccess { doc ->
                    _uiState.update {
                        UploadStockUiState(
                            successMessage = "Booked in — draft #${doc.id} (${doc.portalState.pretty()}).",
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        when (throwable) {
                            is PdaApiException.Validation -> it.copy(
                                isSubmitting = false,
                                error = throwable.message,
                                fieldErrors = throwable.fieldErrors,
                            )

                            is PdaApiException -> it.copy(isSubmitting = false, error = throwable.message)
                            else -> it.copy(
                                isSubmitting = false,
                                error = "Something went wrong. Please try again.",
                            )
                        }
                    }
                }
        }
    }
}
