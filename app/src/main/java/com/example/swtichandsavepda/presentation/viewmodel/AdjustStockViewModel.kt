package com.example.swtichandsavepda.presentation.screens.adjuststock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.local.AttemptState
import com.example.swtichandsavepda.data.local.SubmissionJournal
import com.example.swtichandsavepda.data.model.AdjustmentMode
import com.example.swtichandsavepda.data.model.NewStockAdjustment
import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.StockAdjustmentDoc
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
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
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val outcome: SubmitOutcome? = null,
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
    val baseCostHint: String? get() = unitChoice.baseCostHint(unitCost)

    val canSubmit: Boolean
        get() = !isSubmitting &&
            product != null &&
            // Until the units lookup resolves we do not know what the typed
            // quantity means — see UnitChoice.
            unitChoice.isResolved &&
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
    private val journal: SubmissionJournal,
    /** Stock writes outlive this screen — see [WriteScope]. */
    @WriteScope private val writeScope: CoroutineScope,
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
        _uiState.update { it.copy(mode = mode, outcome = null, fieldErrors = emptyMap()) }
    }

    fun selectProduct(option: ReferenceOption) = selectProduct(option, null)

    fun selectProduct(option: ReferenceOption, preferredProductUnitId: Long?) {
        _uiState.update {
            it.copy(
                product = option,
                // A new product invalidates the old product's units.
                unitChoice = UnitChoice(status = UnitChoice.Status.Loading),
                // Prefill unit cost from the product when this mode needs one.
                unitCost = if (it.mode.requiresUnitCost && it.unitCost.isBlank()) {
                    option.cost?.let { cost -> "%.2f".format(cost) } ?: it.unitCost
                } else {
                    it.unitCost
                },
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
                    // BASE cost; if a non-base unit auto-selects, that figure is
                    // per the wrong unit. Re-prefill or clear rather than leave a
                    // per-piece number in a per-box field.
                    unitCost = if (state.mode.requiresUnitCost) {
                        choice.selected?.let { unit ->
                            unit.purchaseCost?.let { "%.2f".format(it) }
                                ?: if (unit.isBase) state.unitCost else ""
                        } ?: state.unitCost
                    } else {
                        state.unitCost
                    },
                )
            }
        }
    }

    fun selectUnit(unit: ProductUnit) {
        _uiState.update { state ->
            state.copy(
                unitChoice = state.unitChoice.copy(selected = unit),
                // The entered quantity meant the previous unit — clear it rather than
                // silently re-reading "5 Pcs" as "5 Box".
                quantity = "",
                unitCost = unit.purchaseCost?.let { "%.2f".format(it) }
                    ?: if (unit.isBase) state.unitCost else "",
                outcome = null,
            )
        }
    }

    fun selectSourceLocation(option: ReferenceOption) {
        _uiState.update { it.copy(sourceLocation = option, outcome = null) }
    }

    fun selectDestinationLocation(option: ReferenceOption) {
        _uiState.update { it.copy(destinationLocation = option, outcome = null) }
    }

    fun setQuantity(text: String) {
        _uiState.update {
            it.copy(quantity = sanitizeDecimal(text, it.unitChoice.quantityDecimals), outcome = null)
        }
    }

    fun setDestinationShop(text: String) {
        _uiState.update { it.copy(destinationShopId = text.filter(Char::isDigit).take(MAX_DIGITS), outcome = null) }
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
            mode = snapshot.mode,
            // Entered in the selected unit; the repository converts to base.
            quantity = quantity,
            unit = snapshot.unitChoice.unitForRequest,
            sourceLocationId = snapshot.sourceLocation?.id.takeIf { snapshot.mode.requiresSourceLocation },
            destinationLocationId = snapshot.destinationLocation?.id
                .takeIf { snapshot.mode.requiresDestinationLocation },
            destinationShopId = snapshot.destinationShopId.toLongOrNull()
                .takeIf { snapshot.mode.requiresDestinationShop },
            unitCost = snapshot.unitCost.toDoubleOrNull().takeIf { snapshot.mode.requiresUnitCost },
            reason = snapshot.reason.trim().ifBlank { null },
        )

        // Claim atomically: getAndUpdate returns the PREVIOUS value, so exactly
        // one caller observes canSubmit == true and proceeds, regardless of
        // dispatcher. The old read-then-launch guard only held because
        // viewModelScope is Main.immediate — and this write no longer runs there.
        val claimed = _uiState.getAndUpdate { state ->
            if (state.canSubmit) {
                state.copy(isSubmitting = true, outcome = null, fieldErrors = emptyMap())
            } else {
                state
            }
        }
        if (!claimed.canSubmit) return

        val attemptId = newAttemptId()
        val summary = "${snapshot.mode.label} ${UomMath.pretty(quantity)} " +
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
                            outcome = SubmitOutcome.Created(
                                "Adjustment #${doc.id} created — ${doc.portalState.pretty()}.",
                            ),
                        )
                    }
                    loadRecent()
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
                    // actually holds, so put the list in front of the operator.
                    if (throwable is PdaApiException.Ambiguous) loadRecent()
                }
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
        // cancellingId is a single slot: without an atomic claim a second cancel
        // erases the first's in-flight marker and re-enables its button mid-flight.
        val claimed = _uiState.getAndUpdate { state ->
            if (state.cancellingId == null) state.copy(cancellingId = id, outcome = null) else state
        }
        if (claimed.cancellingId != null) return

        writeScope.launch {
            repository.cancel(id)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            cancellingId = null,
                            outcome = SubmitOutcome.Created("Adjustment #$id cancelled."),
                        )
                    }
                    loadRecent()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(cancellingId = null).applyFailure(throwable) }
                }
        }
    }

    /**
     * Maps a failed write onto the form. [PdaApiException.Ambiguous] clears the
     * form rather than leaving it pre-filled with a live Submit button — leaving
     * it ready to re-send is exactly how one adjustment becomes two.
     */
    private fun AdjustStockUiState.applyFailure(throwable: Throwable): AdjustStockUiState =
        when (throwable) {
            is PdaApiException.Ambiguous -> AdjustStockUiState(
                mode = mode,
                recent = recent,
                outcome = SubmitOutcome.Unresolved(
                    "${throwable.message} Check the recent list before entering it again.",
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
        const val MAX_DIGITS = 9
        const val RECENT_LIMIT = 20
        const val DOCUMENT_TYPE = "stock_adjustment"
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
