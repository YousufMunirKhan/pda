package com.example.swtichandsavepda.presentation.screens.purchaseorder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.model.NewPurchaseOrderLine
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.PurchaseOrderDoc
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.repository.PdaPurchaseOrderRepository
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

private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

/**
 * A staged PO line, holding the picked product so the UI can show its name and
 * the unit it was entered in so the request can carry the Multi-UOM fields.
 */
data class PoDraftLine(
    val product: ReferenceOption,
    val quantity: Double,
    val unitCost: Double,
    val unit: ProductUnit? = null,
) {
    /** "24 Box" / "24" — how the staged line reads in the list. */
    val quantityLabel: String
        get() = UomMath.pretty(quantity) + (unit?.takeIf { !it.isBase }?.let { " ${it.label}" } ?: "")
}

data class PurchaseOrderUiState(
    // Create form
    val supplier: ReferenceOption? = null,
    val deliveryDate: String = "",
    val lineProduct: ReferenceOption? = null,
    val lineUnitChoice: UnitChoice = UnitChoice(),
    val lineQuantity: String = "",
    val lineUnitCost: String = "",
    val draftLines: List<PoDraftLine> = emptyList(),
    val isSubmitting: Boolean = false,
    // Existing orders
    val orders: List<PurchaseOrderDoc> = emptyList(),
    val isLoading: Boolean = false,
    val busyOrderId: Long? = null,
    // Messaging
    val error: String? = null,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val successMessage: String? = null,
) {
    val lineBaseQuantityHint: String? get() = lineUnitChoice.baseQuantityHint(lineQuantity)

    val canAddLine: Boolean
        get() = lineProduct != null &&
            (lineQuantity.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (lineUnitCost.toDoubleOrNull() ?: 0.0) > 0.0

    /**
     * Totals are in **base** units and priced per base unit, so a Box line and a
     * Pcs line for the same product add up to something meaningful. The portal
     * computes the authoritative totals server-side; this is the on-screen tally.
     */
    val draftUnits: Double
        get() = draftLines.sumOf { UomMath.baseQuantity(it.quantity, it.unit?.conversionToBase ?: 1.0) }

    val draftValue: Double get() = draftLines.sumOf { it.quantity * it.unitCost }

    val canSubmit: Boolean
        get() = !isSubmitting &&
            supplier != null &&
            DATE_PATTERN.matches(deliveryDate) &&
            draftLines.isNotEmpty()
}

@HiltViewModel
class PurchaseOrderViewModel @Inject constructor(
    private val repository: PdaPurchaseOrderRepository,
    private val referenceRepository: PdaReferenceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseOrderUiState())
    val uiState: StateFlow<PurchaseOrderUiState> = _uiState.asStateFlow()

    init {
        loadOrders()
        // Pre-select the scanned product — and its unit — straight into the line form.
        savedStateHandle.scannedProduct()?.let { scanned ->
            selectLineProduct(scanned.option, scanned.productUnitId)
        }
    }

    // ── Pick-list search (delegated from the pickers) ───────────────────────
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
                // Prefill the unit cost from the product's cost when known.
                lineUnitCost = option.cost?.let { cost -> "%.2f".format(cost) } ?: it.lineUnitCost,
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
                lineUnitCost = unit.purchaseCost?.let { "%.2f".format(it) } ?: state.lineUnitCost,
                error = null,
            )
        }
    }

    fun setDeliveryDate(text: String) {
        _uiState.update { it.copy(deliveryDate = text, error = null) }
    }

    fun setLineQuantity(text: String) {
        _uiState.update {
            it.copy(
                lineQuantity = sanitizeDecimal(text, it.lineUnitChoice.quantityDecimals),
                error = null,
            )
        }
    }

    fun setLineUnitCost(text: String) {
        _uiState.update { it.copy(lineUnitCost = sanitizeDecimal(text), error = null) }
    }

    fun addLine() {
        val state = _uiState.value
        if (!state.canAddLine) return
        val line = PoDraftLine(
            product = state.lineProduct!!,
            quantity = state.lineQuantity.toDouble(),
            unitCost = state.lineUnitCost.toDouble(),
            unit = state.lineUnitChoice.unitForRequest,
        )
        _uiState.update {
            it.copy(
                draftLines = it.draftLines + line,
                lineProduct = null,
                lineUnitChoice = UnitChoice(),
                lineQuantity = "",
                lineUnitCost = "",
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
            NewPurchaseOrderLine(it.product.id, it.quantity, it.unitCost, it.unit)
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isSubmitting = true, error = null, successMessage = null, fieldErrors = emptyMap())
            }
            repository.create(supplierId, state.deliveryDate, lines)
                .onSuccess { doc ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            supplier = null,
                            deliveryDate = "",
                            draftLines = emptyList(),
                            successMessage = "PO ${doc.reference} created — ${doc.portalState.pretty()}.",
                        )
                    }
                    loadOrders()
                }
                .onFailure { throwable -> _uiState.update { it.applyError(throwable) } }
        }
    }

    fun receive(orderId: Long) = mutateOrder(orderId, "received") { repository.receive(orderId) }

    fun cancelOrder(orderId: Long) = mutateOrder(orderId, "cancelled") { repository.cancel(orderId) }

    private fun mutateOrder(
        orderId: Long,
        verb: String,
        action: suspend () -> Result<PurchaseOrderDoc>,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyOrderId = orderId, error = null, successMessage = null) }
            action()
                .onSuccess { doc ->
                    _uiState.update {
                        it.copy(busyOrderId = null, successMessage = "PO ${doc.reference} $verb.")
                    }
                    loadOrders()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(busyOrderId = null).applyError(throwable) }
                }
        }
    }

    fun loadOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.list()
                .onSuccess { orders -> _uiState.update { it.copy(isLoading = false, orders = orders) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false).applyError(throwable) }
                }
        }
    }

    private fun PurchaseOrderUiState.applyError(throwable: Throwable): PurchaseOrderUiState =
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
