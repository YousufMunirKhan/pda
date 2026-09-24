package com.example.swtichandsavepda.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.data.model.AuthState
import com.example.swtichandsavepda.data.model.ProductRef
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.PurchaseOrderReceipt
import com.example.swtichandsavepda.data.model.PurchaseReturnDoc
import com.example.swtichandsavepda.data.repository.PdaAuthRepository
import com.example.swtichandsavepda.printer.PrintDocuments
import com.example.swtichandsavepda.printer.PrinterRepository
import com.example.swtichandsavepda.printer.model.PrintDocument
import com.example.swtichandsavepda.printer.model.PrintResult
import com.example.swtichandsavepda.printer.model.PrinterSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the last print did, for the inline banner. */
data class PrintMessage(val text: String, val isError: Boolean)

data class PrintUiState(
    /** The job on its way to the printer, or null when idle. One at a time. */
    val activeJobKey: String? = null,
    val message: PrintMessage? = null,
) {
    val isPrinting: Boolean get() = activeJobKey != null

    fun isPrinting(jobKey: String): Boolean = activeJobKey == jobKey
}

/** Stable keys so a row can show its own spinner while it prints. */
object PrintJobKeys {
    fun label(product: ProductRef, unit: ProductUnit?) = "label:${product.id}:${unit?.productUnitId ?: 0}"
    fun goodsReceived(receipt: PurchaseOrderReceipt) = "grn:${receipt.id}"
    fun supplierReturn(doc: PurchaseReturnDoc) = "return:${doc.id}"
}

/**
 * Printing for any screen: product labels, GRN slips, return slips.
 *
 * One small ViewModel reused by every screen that prints, instead of spreading
 * print state through each feature's own ViewModel — printing is the same
 * operation wherever it is started from.
 */
@HiltViewModel
class PrintViewModel @Inject constructor(
    private val printerRepository: PrinterRepository,
    private val authRepository: PdaAuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrintUiState())
    val uiState: StateFlow<PrintUiState> = _uiState.asStateFlow()

    val settings: StateFlow<PrinterSettings> = printerRepository.settings

    /** The scan that was last auto-printed, so a recomposition never prints it twice. */
    private var lastAutoPrintedScanId: Int? = null

    private var clearMessageJob: Job? = null

    fun printLabel(product: ProductRef, unit: ProductUnit?, copies: Int) {
        val label = PrintDocuments.productLabel(product, unit)
        startJob(
            jobKey = PrintJobKeys.label(product, unit),
            document = label,
            copies = copies,
            missingDocumentMessage = "This product has no barcode to print.",
            successMessage = if (copies > 1) "$copies labels printed." else "Label printed.",
        )
    }

    /**
     * Called for every resolved scan; prints only when the operator has turned
     * on auto-print. [scanId] identifies the scan, not the product — scanning the
     * same product twice is two scans and prints twice.
     */
    fun onProductScanned(scanId: Int, product: ProductRef, unit: ProductUnit?) {
        val current = settings.value
        if (!current.autoPrintOnScan || scanId == lastAutoPrintedScanId) return
        lastAutoPrintedScanId = scanId
        printLabel(product, unit, current.labelCopies)
    }

    fun printGoodsReceived(receipt: PurchaseOrderReceipt, orderReference: String, supplierName: String?) {
        startJob(
            jobKey = PrintJobKeys.goodsReceived(receipt),
            document = PrintDocuments.goodsReceived(
                receipt = receipt,
                orderReference = orderReference,
                supplierName = supplierName,
                storeName = storeName(),
                printedAtEpochMs = System.currentTimeMillis(),
            ),
            successMessage = "GRN ${receipt.title} printed.",
        )
    }

    fun printSupplierReturn(doc: PurchaseReturnDoc) {
        startJob(
            jobKey = PrintJobKeys.supplierReturn(doc),
            document = PrintDocuments.supplierReturn(
                doc = doc,
                storeName = storeName(),
                printedAtEpochMs = System.currentTimeMillis(),
            ),
            successMessage = "Return ${doc.referenceNo} printed.",
        )
    }

    fun dismissMessage() {
        clearMessageJob?.cancel()
        _uiState.update { it.copy(message = null) }
    }

    private fun startJob(
        jobKey: String,
        document: PrintDocument?,
        copies: Int = 1,
        missingDocumentMessage: String = "Nothing to print.",
        successMessage: String,
    ) {
        if (document == null) {
            showMessage(PrintMessage(missingDocumentMessage, isError = true))
            return
        }
        // Atomic claim: a double tap, or an auto-print landing on a manual one,
        // must not queue a second copy the operator did not ask for.
        val claimed = _uiState.getAndUpdate { state ->
            if (state.activeJobKey == null) state.copy(activeJobKey = jobKey, message = null) else state
        }
        if (claimed.activeJobKey != null) return
        clearMessageJob?.cancel()

        viewModelScope.launch {
            val message = printerRepository.print(document, copies).fold(
                onSuccess = { result ->
                    when (result) {
                        PrintResult.PRINTED -> PrintMessage(successMessage, isError = false)
                        PrintResult.HANDED_OFF -> PrintMessage("Sent to RawBT for printing.", isError = false)
                    }
                },
                onFailure = { error ->
                    PrintMessage(error.message ?: "Printing failed. Try again.", isError = true)
                },
            )
            _uiState.update { it.copy(activeJobKey = null) }
            showMessage(message)
        }
    }

    /** Successes fade on their own; an error stays until the operator has read it. */
    private fun showMessage(message: PrintMessage) {
        _uiState.update { it.copy(message = message) }
        if (message.isError) return
        clearMessageJob = viewModelScope.launch {
            delay(SUCCESS_MESSAGE_MS)
            _uiState.update { state -> if (state.message == message) state.copy(message = null) else state }
        }
    }

    private fun storeName(): String? =
        (authRepository.authState.value as? AuthState.Authenticated)?.tenant?.name?.takeIf { it.isNotBlank() }

    private companion object {
        const val SUCCESS_MESSAGE_MS = 4_000L
    }
}
