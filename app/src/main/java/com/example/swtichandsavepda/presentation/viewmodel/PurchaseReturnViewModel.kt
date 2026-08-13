package com.example.swtichandsavepda.presentation.screens.purchasereturn

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.model.NewPurchaseReturnLine
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.PurchaseReturnDoc
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.repository.PdaPurchaseReturnRepository
import com.example.swtichandsavepda.data.repository.PdaReferenceRepository
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
 * A staged return line, holding the picked product for display and the unit it
 * was entered in so the request can carry the Multi-UOM fields.
 */
data class PrDraftLine(
    val product: ReferenceOption,
    val quantity: Double,
    val costPrice: Double,
    val reason: String,
    val unit: ProductUnit? = null,
) {
    val quantityLabel: String
        get() = UomMath.pretty(quantity) + (unit?.takeIf { !it.isBase }?.let { " ${it.label}" } ?: "")
}

data class PurchaseReturnUiState(
    val supplier: ReferenceOption? = null,
    val referenceNo: String = "",
    val returnReason: String = "",
    val lineProduct: ReferenceOption? = null,
    val lineUnitChoice: UnitChoice = UnitChoice(),
    val lineQuantity: String = "",
    val lineCostPrice: String = "",
    val lineReason: String = "",
    val draftLines: List<PrDraftLine> = emptyList(),
    val isSubmitting: Boolean = false,
    val returns: List<PurchaseReturnDoc> = emptyList(),
    val isLoading: Boolean = false,
    val busyReturnId: Long? = null,
    val error: String? = null,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val successMessage: String? = null,
) {
    val lineBaseQuantityHint: String? get() = lineUnitChoice.baseQuantityHint(lineQuantity)

    val canAddLine: Boolean
        get() = lineProduct != null &&
            (lineQuantity.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (lineCostPrice.toDoubleOrNull() ?: 0.0) > 0.0 &&
            lineReason.isNotBlank()

    val canSubmit: Boolean
        get() = !isSubmitting &&
            supplier != null &&
            referenceNo.isNotBlank() &&
            returnReason.isNotBlank() &&
            draftLines.isNotEmpty()
}

@HiltViewModel
class PurchaseReturnViewModel @Inject constructor(
    private val repository: PdaPurchaseReturnRepository,
    private val referenceRepository: PdaReferenceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseReturnUiState())
    val uiState: StateFlow<PurchaseReturnUiState> = _uiState.asStateFlow()

    init {
        loadReturns()
        // Pre-select the scanned product — and its unit — straight into the line form.
        savedStateHandle.scannedProduct()?.let { scanned ->
            selectLineProduct(scanned.option, scanned.productUnitId)
        }
    }

    suspend fun searchSuppliers(query: String): Result<List<ReferenceOption>> =
        referenceRepository.searchSuppliers(query).map { list -> list.map { it.toOption() } }

    suspend fun searchProducts(query: String): Result<List<ReferenceOption>> =
        referenceRepository.searchProducts(query).map { list -> list.map { it.toOption() } }

    fun selectSupplier(option: ReferenceOption) {
        _uiState.update { it.copy(supplier = option, error = null) }
    }

    fun selectLineProduct(option: ReferenceOption) = selectLineProduct(option, null)

    fun selectLineProduct(option: ReferenceOption, preferredProductUnitId: Long?) {
        _uiState.update {
            it.copy(
                lineProduct = option,
                lineUnitChoice = UnitChoice(),
                lineCostPrice = option.cost?.let { cost -> "%.2f".format(cost) } ?: it.lineCostPrice,
                error = null,
            )
        }
        loadLineUnits(option.id, preferredProductUnitId)
    }

    private fun loadLineUnits(productId: Long, preferredProductUnitId: Long?) {
        viewModelScope.launch {
            val choice = referenceRepository.loadUnitChoice(productId, preferredProductUnitId)
            // Ignore a late response for a product the operator has moved on from.
            _uiState.update {
                if (it.lineProduct?.id == productId) it.copy(lineUnitChoice = choice) else it
            }
        }
    }

    fun selectLineUnit(unit: ProductUnit) {
        _uiState.update { state ->
            state.copy(
                lineUnitChoice = state.lineUnitChoice.copy(selected = unit),
                // The typed quantity and cost meant the previous unit.
                lineQuantity = "",
                lineCostPrice = unit.purchaseCost?.let { "%.2f".format(it) } ?: state.lineCostPrice,
                error = null,
            )
        }
    }

    fun setReference(text: String) {
        _uiState.update { it.copy(referenceNo = text, error = null) }
    }

    fun setReturnReason(text: String) {
        _uiState.update { it.copy(returnReason = text, error = null) }
    }

    fun setLineQuantity(text: String) {
        _uiState.update {
            it.copy(
                lineQuantity = sanitizeDecimal(text, it.lineUnitChoice.quantityDecimals),
                error = null,
            )
        }
    }

    fun setLineCostPrice(text: String) {
        _uiState.update { it.copy(lineCostPrice = sanitizeDecimal(text), error = null) }
    }

    fun setLineReason(text: String) {
        _uiState.update { it.copy(lineReason = text, error = null) }
    }

    fun addLine() {
        val state = _uiState.value
        if (!state.canAddLine) return
        val line = PrDraftLine(
            product = state.lineProduct!!,
            quantity = state.lineQuantity.toDouble(),
            costPrice = state.lineCostPrice.toDouble(),
            reason = state.lineReason.trim(),
            unit = state.lineUnitChoice.unitForRequest,
        )
        _uiState.update {
            it.copy(
                draftLines = it.draftLines + line,
                lineProduct = null,
                lineUnitChoice = UnitChoice(),
                lineQuantity = "",
                lineCostPrice = "",
                lineReason = "",
            )
        }
    }

    fun removeLine(index: Int) {
        _uiState.update {
            if (index !in it.draftLines.indices) it
            else it.copy(draftLines = it.draftLines.filterIndexed { i, _ -> i != index })
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(error = null, successMessage = null, fieldErrors = emptyMap()) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val supplierId = state.supplier?.id ?: return
        val lines = state.draftLines.map {
            NewPurchaseReturnLine(it.product.id, it.quantity, it.costPrice, it.reason, it.unit)
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isSubmitting = true, error = null, successMessage = null, fieldErrors = emptyMap())
            }
            repository.create(
                supplierId = supplierId,
                referenceNo = state.referenceNo.trim(),
                returnReason = state.returnReason.trim(),
                lines = lines,
            )
                .onSuccess { doc ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            supplier = null,
                            referenceNo = "",
                            returnReason = "",
                            draftLines = emptyList(),
                            successMessage = "Return ${doc.referenceNo} created — ${doc.portalState.pretty()}.",
                        )
                    }
                    loadReturns()
                }
                .onFailure { throwable -> _uiState.update { it.applyError(throwable) } }
        }
    }

    fun cancelReturn(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyReturnId = id, error = null, successMessage = null) }
            repository.cancel(id)
                .onSuccess { doc ->
                    _uiState.update {
                        it.copy(busyReturnId = null, successMessage = "Return ${doc.referenceNo} cancelled.")
                    }
                    loadReturns()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(busyReturnId = null).applyError(throwable) }
                }
        }
    }

    fun loadReturns() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.list()
                .onSuccess { returns -> _uiState.update { it.copy(isLoading = false, returns = returns) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false).applyError(throwable) }
                }
        }
    }

    private fun PurchaseReturnUiState.applyError(throwable: Throwable): PurchaseReturnUiState =
        when (throwable) {
            is PdaApiException.Validation -> copy(
                isSubmitting = false,
                error = throwable.message,
                fieldErrors = throwable.fieldErrors,
            )

            is PdaApiException -> copy(isSubmitting = false, error = throwable.message)
            else -> copy(isSubmitting = false, error = "Something went wrong. Please try again.")
        }
}
