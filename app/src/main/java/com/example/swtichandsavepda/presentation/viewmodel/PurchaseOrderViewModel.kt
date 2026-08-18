package com.example.swtichandsavepda.presentation.screens.purchaseorder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.local.AttemptState
import com.example.swtichandsavepda.data.local.SubmissionJournal
import com.example.swtichandsavepda.data.model.NewPurchaseOrderLine
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.PurchaseOrderDoc
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.data.remote.PdaApiException
import com.example.swtichandsavepda.data.repository.PdaPurchaseOrderRepository
import com.example.swtichandsavepda.data.repository.PdaReferenceRepository
import com.example.swtichandsavepda.di.WriteScope
import com.example.swtichandsavepda.presentation.ReceiptHistory
import com.example.swtichandsavepda.presentation.ReceiveDraft
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
    /** The goods-in currently being entered, or null when the sheet is closed. */
    val receiveDraft: ReceiveDraft? = null,
    /** The receiving history being viewed, or null when that sheet is closed. */
    val receiptHistory: ReceiptHistory? = null,
    val isLoading: Boolean = false,
    val busyOrderId: Long? = null,
    // Messaging
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val outcome: SubmitOutcome? = null,
) {
    val lineBaseQuantityHint: String? get() = lineUnitChoice.baseQuantityHint(lineQuantity)

    val lineBaseCostHint: String? get() = lineUnitChoice.baseCostHint(lineUnitCost)

    val canAddLine: Boolean
        get() = lineProduct != null &&
            // Until the units lookup resolves we do not know what the typed
            // quantity means — see UnitChoice.
            lineUnitChoice.isResolved &&
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
    private val journal: SubmissionJournal,
    /** Stock writes outlive this screen — see [WriteScope]. */
    @WriteScope private val writeScope: CoroutineScope,
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
        _uiState.update { it.copy(supplier = option, outcome = null) }
    }

    fun selectLineProduct(option: ReferenceOption) = selectLineProduct(option, null)

    fun selectLineProduct(option: ReferenceOption, preferredProductUnitId: Long?) {
        _uiState.update {
            it.copy(
                lineProduct = option,
                lineUnitChoice = UnitChoice(status = UnitChoice.Status.Loading),
                // Prefill the unit cost from the product's cost when known.
                lineUnitCost = option.cost?.let { cost -> "%.2f".format(cost) } ?: it.lineUnitCost,
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
                    lineUnitCost = choice.selected?.let { unit ->
                        unit.purchaseCost?.let { "%.2f".format(it) }
                            ?: if (unit.isBase) state.lineUnitCost else ""
                    } ?: state.lineUnitCost,
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
                lineUnitCost = unit.purchaseCost?.let { "%.2f".format(it) }
                    ?: if (unit.isBase) state.lineUnitCost else "",
                outcome = null,
            )
        }
    }

    fun setDeliveryDate(text: String) {
        _uiState.update { it.copy(deliveryDate = text, outcome = null) }
    }

    fun setLineQuantity(text: String) {
        _uiState.update {
            it.copy(
                lineQuantity = sanitizeDecimal(text, it.lineUnitChoice.quantityDecimals),
                outcome = null,
            )
        }
    }

    fun setLineUnitCost(text: String) {
        _uiState.update { it.copy(lineUnitCost = sanitizeDecimal(text), outcome = null) }
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
        _uiState.update { it.copy(outcome = null, fieldErrors = emptyMap()) }
    }

    fun submit() {
        // Build the request from a snapshot first, so none of the early returns
        // below can leave isSubmitting stuck true after the claim.
        val snapshot = _uiState.value
        if (!snapshot.canSubmit) return
        val supplier = snapshot.supplier ?: return
        val lines = snapshot.draftLines.map {
            NewPurchaseOrderLine(it.product.id, it.quantity, it.unitCost, it.unit)
        }

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
        val summary = "PO · ${supplier.title} · ${lines.size} line(s)"

        writeScope.launch {
            // Journalled BEFORE the request, so a document can never exist on the
            // portal without a local record that we tried.
            journal.begin(attemptId, DOCUMENT_TYPE, summary, System.currentTimeMillis())

            repository.create(supplier.id, snapshot.deliveryDate, lines)
                .onSuccess { doc ->
                    journal.resolve(attemptId, AttemptState.CREATED, doc.id)
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            supplier = null,
                            deliveryDate = "",
                            draftLines = emptyList(),
                            outcome = SubmitOutcome.Created(
                                "PO ${doc.reference} created — ${doc.portalState.pretty()}.",
                            ),
                        )
                    }
                    loadOrders()
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
                    if (throwable is PdaApiException.Ambiguous) loadOrders()
                }
        }
    }

    // ── Receiving ───────────────────────────────────────────────────────────

    /**
     * Opens the goods-in sheet for a PO. A PO can be received more than once, so
     * this is always about *this* delivery, never the whole order.
     */
    fun startReceive(orderId: Long) {
        val order = _uiState.value.orders.firstOrNull { it.id == orderId } ?: return
        if (!order.canReceive) return
        _uiState.update { it.copy(receiveDraft = ReceiveDraft.of(order), outcome = null) }
    }

    fun setReceiveQuantity(index: Int, text: String) {
        _uiState.update { state ->
            val draft = state.receiveDraft ?: return@update state
            val line = draft.lines.getOrNull(index) ?: return@update state
            // Whole units unless the line's unit allows decimals; the PO carries
            // no allow_decimal, so mirror the entered unit's own precision.
            val decimals = if (line.remaining % 1.0 == 0.0) 0 else MAX_RECEIVE_DECIMALS
            state.copy(receiveDraft = draft.withEntry(index, sanitizeDecimal(text, decimals)))
        }
    }

    /** "Receive all outstanding" — the common case when a full delivery lands. */
    fun fillReceiveRemaining() {
        _uiState.update { state ->
            state.copy(receiveDraft = state.receiveDraft?.fillRemaining())
        }
    }

    /**
     * Opens the receiving history for a PO and fetches it on demand — a list of
     * twenty orders should not pull twenty histories nobody will open.
     */
    fun showReceiptHistory(orderId: Long) {
        val order = _uiState.value.orders.firstOrNull { it.id == orderId } ?: return
        _uiState.update {
            it.copy(
                receiptHistory = ReceiptHistory(
                    orderId = orderId,
                    orderReference = order.reference,
                    isLoading = true,
                ),
            )
        }
        loadReceiptHistory(orderId)
    }

    fun retryReceiptHistory() {
        val open = _uiState.value.receiptHistory ?: return
        _uiState.update { it.copy(receiptHistory = open.copy(isLoading = true, error = null)) }
        loadReceiptHistory(open.orderId)
    }

    private fun loadReceiptHistory(orderId: Long) {
        viewModelScope.launch {
            repository.receipts(orderId)
                .onSuccess { receipts ->
                    _uiState.update { state ->
                        // Ignore a late response for a sheet the operator closed
                        // or reopened on a different order.
                        val open = state.receiptHistory?.takeIf { it.orderId == orderId }
                            ?: return@update state
                        state.copy(
                            receiptHistory = open.copy(receipts = receipts, isLoading = false),
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        val open = state.receiptHistory?.takeIf { it.orderId == orderId }
                            ?: return@update state
                        state.copy(
                            receiptHistory = open.copy(
                                isLoading = false,
                                error = throwable.message ?: "Couldn't load the receiving history.",
                            ),
                        )
                    }
                }
        }
    }

    fun dismissReceiptHistory() {
        _uiState.update { it.copy(receiptHistory = null) }
    }

    fun setReceiveDeliveryNote(text: String) {
        _uiState.update { state ->
            state.copy(receiveDraft = state.receiveDraft?.withDeliveryNote(text))
        }
    }

    fun cancelReceive() {
        _uiState.update { it.copy(receiveDraft = null) }
    }

    /**
     * Posts this delivery. The success message quotes the **portal's** returned
     * received/ordered totals rather than the app's own arithmetic, because
     * whether the portal adds to or replaces `quantity_received` is unconfirmed
     * (API addendum §2.1) — so the operator is shown what the portal actually
     * recorded, whichever way it behaves.
     */
    fun confirmReceive() {
        val draft = _uiState.value.receiveDraft ?: return
        if (!draft.canSubmit) return
        val orderId = draft.order.id
        val quantities = draft.receivedByProduct()
        val deliveryNote = draft.deliveryNote.trim().ifBlank { null }
        val attemptId = newAttemptId()
        val summary = "Receive " + UomMath.pretty(draft.enteredTotal) +
            " · " + draft.order.reference

        val claimed = _uiState.getAndUpdate { state ->
            if (state.busyOrderId == null) {
                state.copy(busyOrderId = orderId, receiveDraft = null, outcome = null)
            } else {
                state
            }
        }
        if (claimed.busyOrderId != null) return

        writeScope.launch {
            // Journalled before the request, and the same id doubles as the
            // portal's idempotency key: a receive is a delta the portal
            // accumulates, so an unguarded retry books the delivery twice.
            journal.begin(attemptId, DOCUMENT_TYPE_RECEIPT, summary, System.currentTimeMillis())

            repository.receive(
                id = orderId,
                receivedByProduct = quantities,
                referenceNo = deliveryNote,
                clientReference = attemptId,
            )
                .onSuccess { doc ->
                    journal.resolve(attemptId, AttemptState.CREATED, doc.id)
                    _uiState.update {
                        it.copy(
                            busyOrderId = null,
                            outcome = SubmitOutcome.Created(
                                "PO ${doc.reference} — ${UomMath.pretty(doc.receivedTotal)} of " +
                                    "${UomMath.pretty(doc.orderedTotal)} received, " +
                                    "${UomMath.pretty(doc.remainingTotal)} outstanding.",
                            ),
                        )
                    }
                    loadOrders()
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
                    _uiState.update { it.copy(busyOrderId = null).applyFailure(throwable) }
                    // A receive that may or may not have landed must be checked
                    // against the portal before anyone receives again. OVER_RECEIPT
                    // and PO_CANCELLED mean the same thing for a different reason:
                    // somebody else moved this PO while the sheet was open, so the
                    // quantities on screen are stale.
                    val stale = (throwable as? PdaApiException.Validation)?.isStaleData == true
                    if (throwable is PdaApiException.Ambiguous || stale) loadOrders()
                }
        }
    }

    fun cancelOrder(orderId: Long) = mutateOrder(orderId, "cancelled") { repository.cancel(orderId) }

    private fun mutateOrder(
        orderId: Long,
        verb: String,
        action: suspend () -> Result<PurchaseOrderDoc>,
    ) {
        // busyOrderId is a single slot: without an atomic claim, starting a second
        // mutation erases the first's in-flight marker and re-enables its buttons
        // mid-flight. Receive is the one PO operation that moves stock.
        val claimed = _uiState.getAndUpdate { state ->
            if (state.busyOrderId == null) state.copy(busyOrderId = orderId, outcome = null) else state
        }
        if (claimed.busyOrderId != null) return

        writeScope.launch {
            action()
                .onSuccess { doc ->
                    _uiState.update {
                        it.copy(
                            busyOrderId = null,
                            outcome = SubmitOutcome.Created("PO ${doc.reference} $verb."),
                        )
                    }
                    loadOrders()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(busyOrderId = null).applyFailure(throwable) }
                    if (throwable is PdaApiException.Ambiguous) loadOrders()
                }
        }
    }

    fun loadOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.list()
                .onSuccess { orders -> _uiState.update { it.copy(isLoading = false, orders = orders) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false).applyFailure(throwable) }
                }
        }
    }

    /**
     * Maps a failed write onto the form. [PdaApiException.Ambiguous] clears the
     * draft rather than leaving it ready to re-send, which is how one PO becomes
     * two.
     */
    private fun PurchaseOrderUiState.applyFailure(throwable: Throwable): PurchaseOrderUiState =
        when (throwable) {
            is PdaApiException.Ambiguous -> PurchaseOrderUiState(
                orders = orders,
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
        const val DOCUMENT_TYPE = "purchase_order"

        /** Receipts journal under their own type so the menu can name them. */
        const val DOCUMENT_TYPE_RECEIPT = "purchase_order_receipt"

        /** Decimal places allowed when a line's outstanding quantity is fractional. */
        const val MAX_RECEIVE_DECIMALS = 4
    }
}
