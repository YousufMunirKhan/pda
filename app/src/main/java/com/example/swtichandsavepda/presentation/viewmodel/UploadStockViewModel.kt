package com.example.swtichandsavepda.presentation.screens.uploadstock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.local.AttemptState
import com.example.swtichandsavepda.data.local.SubmissionJournal
import com.example.swtichandsavepda.data.model.AdjustmentMode
import com.example.swtichandsavepda.data.model.NewStockAdjustment
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.repository.PdaReferenceRepository
import com.example.swtichandsavepda.data.repository.PdaStockAdjustmentRepository
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
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val outcome: SubmitOutcome? = null,
) {
    val baseQuantityHint: String? get() = unitChoice.baseQuantityHint(quantity)
    val baseCostHint: String? get() = unitChoice.baseCostHint(unitCost)

    val canSubmit: Boolean
        get() = !isSubmitting &&
            product != null &&
            // Until the units lookup resolves we do not know what the typed
            // quantity means — see UnitChoice.
            unitChoice.isResolved &&
            (quantity.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (unitCost.toDoubleOrNull() ?: 0.0) > 0.0
}

@HiltViewModel
class UploadStockViewModel @Inject constructor(
    private val repository: PdaStockAdjustmentRepository,
    private val referenceRepository: PdaReferenceRepository,
    private val journal: SubmissionJournal,
    /** Stock writes outlive this screen — see [WriteScope]. */
    @WriteScope private val writeScope: CoroutineScope,
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
                unitChoice = UnitChoice(status = UnitChoice.Status.Loading),
                unitCost = option.cost?.let { cost -> "%.2f".format(cost) } ?: "",
                outcome = null,
            )
        }
        loadUnits(option.id, preferredProductUnitId)
    }

    /** Retry after a failed units lookup, from the banner the screen shows. */
    fun retryUnits() {
        val productId = _uiState.value.product?.id ?: return
        _uiState.update { it.copy(unitChoice = UnitChoice(status = UnitChoice.Status.Loading)) }
        loadUnits(productId, null)
    }

    private fun loadUnits(productId: Long, preferredProductUnitId: Long?) {
        viewModelScope.launch {
            val choice = referenceRepository.loadUnitChoice(productId, preferredProductUnitId)
            _uiState.update { state ->
                // Ignore a late response for a product the operator has moved on from.
                if (state.product?.id != productId) return@update state
                state.copy(
                    unitChoice = choice,
                    // The prefill applied on product selection was the product's
                    // BASE cost. If a non-base unit auto-selects (a scanned box
                    // barcode, or the first-row fallback) that figure is per the
                    // wrong unit, so re-prefill from the unit or clear it rather
                    // than leave a per-piece number in a per-box field.
                    unitCost = choice.selected?.let { unit ->
                        unit.purchaseCost?.let { "%.2f".format(it) }
                            ?: if (unit.isBase) state.unitCost else ""
                    } ?: state.unitCost,
                )
            }
        }
    }

    fun selectUnit(unit: ProductUnit) {
        _uiState.update { state ->
            state.copy(
                unitChoice = state.unitChoice.copy(selected = unit),
                // The typed quantity and cost meant the previous unit.
                quantity = "",
                unitCost = unit.purchaseCost?.let { "%.2f".format(it) } ?: "",
                outcome = null,
            )
        }
    }

    fun setQuantity(text: String) {
        _uiState.update {
            it.copy(quantity = sanitizeDecimal(text, it.unitChoice.quantityDecimals), outcome = null)
        }
    }

    fun setUnitCost(text: String) {
        _uiState.update { it.copy(unitCost = sanitizeDecimal(text), outcome = null) }
    }

    fun setReason(text: String) {
        _uiState.update { it.copy(reason = text, outcome = null) }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(outcome = null, fieldErrors = emptyMap()) }
    }

    fun submit() {
        // Build the request from a snapshot first, so none of the early returns
        // below can leave isSubmitting stuck true after the claim.
        val snapshot = _uiState.value
        if (!snapshot.canSubmit) return
        val product = snapshot.product ?: return
        val quantity = snapshot.quantity.toDoubleOrNull() ?: return

        val adjustment = NewStockAdjustment(
            productId = product.id,
            mode = AdjustmentMode.INCREASE,
            quantity = quantity,
            unit = snapshot.unitChoice.unitForRequest,
            unitCost = snapshot.unitCost.toDoubleOrNull(),
            reason = snapshot.reason.trim().ifBlank { "Goods in (PDA)" },
        )

        // Claim the submission atomically: getAndUpdate returns the PREVIOUS
        // value, so exactly one caller observes canSubmit == true and proceeds,
        // regardless of dispatcher. The old read-then-launch guard only held
        // because viewModelScope is Main.immediate — and this write no longer
        // runs there.
        val claimed = _uiState.getAndUpdate { state ->
            if (state.canSubmit) {
                state.copy(isSubmitting = true, outcome = null, fieldErrors = emptyMap())
            } else {
                state
            }
        }
        if (!claimed.canSubmit) return

        val attemptId = newAttemptId()
        val summary = "Book in ${UomMath.pretty(quantity)} " +
            "${snapshot.unitChoice.selected?.label ?: "units"} · ${product.title}"

        writeScope.launch {
            // Journalled BEFORE the request, so a document can never exist on the
            // portal without a local record that we tried.
            journal.begin(attemptId, DOCUMENT_TYPE, summary, System.currentTimeMillis())

            // The journal id doubles as the portal's idempotency key, so a local
            // record and the document it produced share one handle.
            repository.create(adjustment, clientReference = attemptId)
                .onSuccess { doc ->
                    journal.resolve(attemptId, AttemptState.CREATED, doc.id)
                    _uiState.value = UploadStockUiState(
                        outcome = SubmitOutcome.Created(
                            "Booked in — draft #${doc.id} (${doc.portalState.pretty()}).",
                        ),
                    )
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
                }
        }
    }

    private companion object {
        const val DOCUMENT_TYPE = "stock_adjustment"
    }
}

/**
 * Maps a failed write onto the form.
 *
 * [PdaApiException.Ambiguous] is the case that matters: the portal could not
 * tell us whether the draft exists, so the form is **cleared** rather than left
 * pre-filled with a live Submit button. Leaving it ready to re-send is exactly
 * how one goods-in becomes two.
 */
internal fun UploadStockUiState.applyFailure(throwable: Throwable): UploadStockUiState =
    when (throwable) {
        is PdaApiException.Ambiguous -> UploadStockUiState(
            outcome = SubmitOutcome.Unresolved(
                "${throwable.message} Check Recent adjustments before booking it in again.",
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
