package com.example.swtichandsavepda.presentation.screens.purchasereturn

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.local.AttemptState
import com.example.swtichandsavepda.data.local.SubmissionJournal
import com.example.swtichandsavepda.data.model.NewPurchaseReturnLine
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.PurchaseReturnDoc
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.repository.PdaPurchaseReturnRepository
import com.example.swtichandsavepda.data.repository.PdaReferenceRepository
import com.example.swtichandsavepda.di.WriteScope
import com.example.swtichandsavepda.presentation.SubmitOutcome
import com.example.swtichandsavepda.presentation.UnitChoice
import com.example.swtichandsavepda.presentation.components.ReferenceOption
import com.example.swtichandsavepda.presentation.components.toOption
import com.example.swtichandsavepda.presentation.loadUnitChoice
import com.example.swtichandsavepda.presentation.newAttemptId
import com.example.swtichandsavepda.presentation.scannedProduct
import com.example.swtichandsavepda.presentation.screens.adjuststock.pretty
import com.example.swtichandsavepda.presentation.screens.adjuststock.sanitizeDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
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
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val outcome: SubmitOutcome? = null,
) {
    val lineBaseQuantityHint: String? get() = lineUnitChoice.baseQuantityHint(lineQuantity)

    val lineBaseCostHint: String? get() = lineUnitChoice.baseCostHint(lineCostPrice)

    val canAddLine: Boolean
        get() = lineProduct != null &&
            // Until the units lookup resolves we do not know what the typed
            // quantity means - see UnitChoice.
            lineUnitChoice.isResolved &&
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
    private val journal: SubmissionJournal,
    /** Stock writes outlive this screen - see [WriteScope]. */
    @WriteScope private val writeScope: CoroutineScope,
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
        _uiState.update { it.copy(supplier = option, outcome = null) }
    }

    fun selectLineProduct(option: ReferenceOption) = selectLineProduct(option, null)

    fun selectLineProduct(option: ReferenceOption, preferredProductUnitId: Long?) {
        _uiState.update {
            it.copy(
                lineProduct = option,
                lineUnitChoice = UnitChoice(status = UnitChoice.Status.Loading),
                lineCostPrice = option.cost?.let { cost -> "%.2f".format(cost) } ?: it.lineCostPrice,
                outcome = null,
            )
        }
        loadLineUnits(option.id, preferredProductUnitId)
    }

    /** Retry after a failed units lookup, from the banner the screen shows. */
    fun retryLineUnits() {
        val productId = _uiState.value.lineProduct?.id ?: return
        _uiState.update { it.copy(lineUnitChoice = UnitChoice(status = UnitChoice.Status.Loading)) }
        loadLineUnits(productId, null)
    }

    private fun loadLineUnits(productId: Long, preferredProductUnitId: Long?) {
        viewModelScope.launch {
            val choice = referenceRepository.loadUnitChoice(productId, preferredProductUnitId)
            _uiState.update { state ->
                // Ignore a late response for a product the operator has moved on from.
                if (state.lineProduct?.id != productId) return@update state
                state.copy(
                    lineUnitChoice = choice,
                    // The prefill applied on product selection was the product's
                    // BASE cost; if a non-base unit auto-selects, that figure is
                    // per the wrong unit.
                    lineCostPrice = choice.selected?.let { unit ->
                        unit.purchaseCost?.let { "%.2f".format(it) }
                            ?: if (unit.isBase) state.lineCostPrice else ""
                    } ?: state.lineCostPrice,
                )
            }
        }
    }

    fun selectLineUnit(unit: ProductUnit) {
        _uiState.update { state ->
            state.copy(
                lineUnitChoice = state.lineUnitChoice.copy(selected = unit),
                // The typed quantity and cost meant the previous unit.
                lineQuantity = "",
                lineCostPrice = unit.purchaseCost?.let { "%.2f".format(it) }
                    ?: if (unit.isBase) state.lineCostPrice else "",
                outcome = null,
            )
        }
    }

    fun setReference(text: String) {
        _uiState.update { it.copy(referenceNo = text, outcome = null) }
    }

    fun setReturnReason(text: String) {
        _uiState.update { it.copy(returnReason = text, outcome = null) }
    }

    fun setLineQuantity(text: String) {
        _uiState.update {
            it.copy(
                lineQuantity = sanitizeDecimal(text, it.lineUnitChoice.quantityDecimals),
                outcome = null,
            )
        }
    }

    fun setLineCostPrice(text: String) {
        _uiState.update { it.copy(lineCostPrice = sanitizeDecimal(text), outcome = null) }
    }

    fun setLineReason(text: String) {
        _uiState.update { it.copy(lineReason = text, outcome = null) }
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
        _uiState.update { it.copy(outcome = null, fieldErrors = emptyMap()) }
    }

    fun submit() {
        // Build the request from a snapshot first, so none of the early returns
        // below can leave isSubmitting stuck true after the claim.
        val snapshot = _uiState.value
        if (!snapshot.canSubmit) return
        val supplier = snapshot.supplier ?: return
        val referenceNo = snapshot.referenceNo.trim()
        val lines = snapshot.draftLines.map {
            NewPurchaseReturnLine(it.product.id, it.quantity, it.costPrice, it.reason, it.unit)
        }

        // Claim atomically: getAndUpdate returns the PREVIOUS value, so exactly
        // one caller observes canSubmit == true and proceeds, regardless of
        // dispatcher. The old read-then-launch guard only held because
        // viewModelScope is Main.immediate - and this write no longer runs there.
        val claimed = _uiState.getAndUpdate { state ->
            if (state.canSubmit) {
                state.copy(isSubmitting = true, outcome = null, fieldErrors = emptyMap())
            } else {
                state
            }
        }
        if (!claimed.canSubmit) return

        val attemptId = newAttemptId()

        writeScope.launch {
            // Journalled BEFORE the request, so a document can never exist on the
            // portal without a local record that we tried.
            journal.begin(
                attemptId,
                DOCUMENT_TYPE,
                "Return $referenceNo · ${supplier.title}",
                System.currentTimeMillis(),
            )

            repository.create(
                supplierId = supplier.id,
                referenceNo = referenceNo,
                returnReason = snapshot.returnReason.trim(),
                lines = lines,
            )
                .onSuccess { doc ->
                    journal.resolve(attemptId, AttemptState.CREATED, doc.id)
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            supplier = null,
                            referenceNo = "",
                            returnReason = "",
                            draftLines = emptyList(),
                            outcome = SubmitOutcome.Created(
                                "Return ${doc.referenceNo} created — ${doc.portalState.pretty()}.",
                            ),
                        )
                    }
                    loadReturns()
                }
                .onFailure { throwable ->
                    journal.resolve(
                        attemptId,
                        if (throwable is PdaApiException.Ambiguous) {
                            AttemptState.UNKNOWN
                        } else {
                            AttemptState.NOT_CREATED
                        },
                    )
                    _uiState.update { it.applyFailure(throwable) }
                    // An unresolved write must be checked against what the portal
                    // actually holds, so refresh the list in front of the operator.
                    if (throwable is PdaApiException.Ambiguous) loadReturns()
                }
        }
    }

    fun cancelReturn(id: Long) {
        // busyReturnId is a single slot: without an atomic claim a second cancel
        // erases the first's in-flight marker and re-enables its button mid-flight.
        val claimed = _uiState.getAndUpdate { state ->
            if (state.busyReturnId == null) state.copy(busyReturnId = id, outcome = null) else state
        }
        if (claimed.busyReturnId != null) return

        writeScope.launch {
            repository.cancel(id)
                .onSuccess { doc ->
                    _uiState.update {
                        it.copy(
                            busyReturnId = null,
                            outcome = SubmitOutcome.Created("Return ${doc.referenceNo} cancelled."),
                        )
                    }
                    loadReturns()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(busyReturnId = null).applyFailure(throwable) }
                }
        }
    }

    fun loadReturns() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.list()
                .onSuccess { returns -> _uiState.update { it.copy(isLoading = false, returns = returns) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false).applyFailure(throwable) }
                }
        }
    }

    /**
     * Maps a failed write onto the form. [PdaApiException.Ambiguous] clears the
     * draft rather than leaving it ready to re-send, which is how one return
     * becomes two.
     */
    private fun PurchaseReturnUiState.applyFailure(throwable: Throwable): PurchaseReturnUiState =
        when (throwable) {
            is PdaApiException.Ambiguous -> PurchaseReturnUiState(
                returns = returns,
                outcome = SubmitOutcome.Unresolved(
                    "${throwable.message} Check the list below before creating it again.",
                ),
            )

            is PdaApiException.Validation -> copy(
                isSubmitting = false,
                outcome = SubmitOutcome.Failed(throwable.message.orEmpty()),
                fieldErrors = throwable.fieldErrors,
            )

            is PdaApiException -> copy(
                isSubmitting = false,
                outcome = SubmitOutcome.Failed(throwable.message.orEmpty()),
            )

            else -> copy(
                isSubmitting = false,
                outcome = SubmitOutcome.Failed("Something went wrong. Please try again."),
            )
        }

    private companion object {
        const val DOCUMENT_TYPE = "purchase_return"
    }
}
