package com.example.swtichandsavepda.presentation.screens.printer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swtichandsavepda.presentation.PrintMessage
import com.example.swtichandsavepda.printer.PrinterRepository
import com.example.swtichandsavepda.printer.escpos.PrintRenderer
import com.example.swtichandsavepda.printer.model.BluetoothPrinter
import com.example.swtichandsavepda.printer.model.LabelTextSize
import com.example.swtichandsavepda.printer.model.PrintDocument
import com.example.swtichandsavepda.printer.model.PrintResult
import com.example.swtichandsavepda.printer.model.PrinterConnection
import com.example.swtichandsavepda.printer.model.PrinterSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrinterSettingsUiState(
    val settings: PrinterSettings = PrinterSettings(),
    val pairedPrinters: List<BluetoothPrinter> = emptyList(),
    /** Why the paired list could not be read (Bluetooth off, no permission, …). */
    val bluetoothError: String? = null,
    val isRawBtInstalled: Boolean = false,
    /** What the operator is typing, so "3" on the way to "32" is not clamped mid-edit. */
    val labelLengthInput: String = PrinterSettings.DEFAULT_LABEL_LENGTH_MM.toString(),
    val labelWidthInput: String = PrinterSettings.DEFAULT_LABEL_WIDTH_MM.toString(),
    val isTesting: Boolean = false,
    val message: PrintMessage? = null,
)

@HiltViewModel
class PrinterSettingsViewModel @Inject constructor(
    private val repository: PrinterRepository,
) : ViewModel() {

    private val local = MutableStateFlow(
        PrinterSettingsUiState(
            labelLengthInput = repository.settings.value.labelLengthMm.toString(),
            labelWidthInput = repository.settings.value.labelWidthMm.toString(),
        ),
    )

    val uiState: StateFlow<PrinterSettingsUiState> =
        combine(local, repository.settings) { state, settings -> state.copy(settings = settings) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    init {
        refresh()
    }

    /** Re-reads paired devices — after pairing in system settings or granting permission. */
    fun refresh() {
        val printers = repository.pairedPrinters()
        local.update {
            it.copy(
                pairedPrinters = printers.getOrDefault(emptyList()),
                bluetoothError = printers.exceptionOrNull()?.message,
                isRawBtInstalled = repository.isRawBtInstalled(),
            )
        }
        autoSelectBuiltInPrinter(printers.getOrDefault(emptyList()))
    }

    /**
     * First run on a handheld POS: exactly one paired device looks like a
     * printer, so it is almost certainly the built-in one. Picking it saves the
     * operator a step; they can still change it.
     */
    private fun autoSelectBuiltInPrinter(printers: List<BluetoothPrinter>) {
        if (repository.settings.value.bluetoothAddress != null) return
        val candidate = printers.filter { it.looksLikePrinter }.singleOrNull() ?: return
        selectBluetoothPrinter(candidate)
    }

    fun selectConnection(connection: PrinterConnection) {
        repository.updateSettings { it.copy(connection = connection) }
    }

    fun selectBluetoothPrinter(printer: BluetoothPrinter) {
        repository.updateSettings {
            it.copy(
                connection = PrinterConnection.BLUETOOTH,
                bluetoothAddress = printer.address,
                bluetoothName = printer.name,
            )
        }
    }

    fun setLabelLength(text: String) {
        val digits = text.filter(Char::isDigit).take(3)
        local.update { it.copy(labelLengthInput = digits) }
        val millimetres = digits.toIntOrNull() ?: return
        if (millimetres in PrinterSettings.MIN_LABEL_LENGTH_MM..PrinterSettings.MAX_LABEL_LENGTH_MM) {
            repository.updateSettings { it.copy(labelLengthMm = millimetres) }
        }
    }

    fun setLabelWidth(text: String) {
        val digits = text.filter(Char::isDigit).take(2)
        local.update { it.copy(labelWidthInput = digits) }
        val millimetres = digits.toIntOrNull() ?: return
        if (millimetres in PrinterSettings.MIN_LABEL_WIDTH_MM..PrinterSettings.MAX_LABEL_WIDTH_MM) {
            repository.updateSettings { it.copy(labelWidthMm = millimetres) }
        }
    }

    fun setLabelTextSize(size: LabelTextSize) = repository.updateSettings { it.copy(labelTextSize = size) }

    fun setGapSensor(enabled: Boolean) = repository.updateSettings { it.copy(useGapSensor = enabled) }

    fun setAutoPrint(enabled: Boolean) = repository.updateSettings { it.copy(autoPrintOnScan = enabled) }

    fun setLabelCopies(copies: Int) = repository.updateSettings {
        it.copy(labelCopies = copies.coerceIn(1, PrinterSettings.MAX_COPIES))
    }

    fun printTestPage() = runTest(
        PrintDocument.TestPage(
            connectionLabel = connectionLabel(repository.settings.value),
            printedAtEpochMs = System.currentTimeMillis(),
        ),
        successText = "Test page printed.",
    )

    /** One sample sticker at the current length — how the operator calibrates alignment. */
    fun printSampleLabel() = runTest(
        PrintDocument.ProductLabel(
            productName = "Sample product",
            barcode = PrintRenderer.SAMPLE_EAN_13,
            price = SAMPLE_PRICE,
        ),
        successText = "Sample label printed. Adjust the label length if it drifts.",
    )

    fun dismissMessage() = local.update { it.copy(message = null) }

    private fun runTest(document: PrintDocument, successText: String) {
        if (local.value.isTesting) return
        local.update { it.copy(isTesting = true, message = null) }
        viewModelScope.launch {
            val message = repository.print(document).fold(
                onSuccess = { result ->
                    val text = if (result == PrintResult.HANDED_OFF) "Sent to RawBT for printing." else successText
                    PrintMessage(text, isError = false)
                },
                onFailure = { PrintMessage(it.message ?: "Printing failed.", isError = true) },
            )
            local.update { it.copy(isTesting = false, message = message) }
        }
    }

    private fun connectionLabel(settings: PrinterSettings): String = when (settings.connection) {
        PrinterConnection.BLUETOOTH -> settings.bluetoothName ?: settings.bluetoothAddress ?: "Bluetooth"
        PrinterConnection.RAWBT -> "RawBT"
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SAMPLE_PRICE = 1.99
    }
}
