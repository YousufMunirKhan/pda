package com.example.swtichandsavepda.printer

import com.example.swtichandsavepda.printer.escpos.PrintRenderer
import com.example.swtichandsavepda.printer.model.BluetoothPrinter
import com.example.swtichandsavepda.printer.model.PrintDocument
import com.example.swtichandsavepda.printer.model.PrintResult
import com.example.swtichandsavepda.printer.model.PrinterConnection
import com.example.swtichandsavepda.printer.model.PrinterSettings
import com.example.swtichandsavepda.printer.transport.BluetoothPrinters
import com.example.swtichandsavepda.printer.transport.PrinterTransport
import com.example.swtichandsavepda.printer.transport.RawBtBridge
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the app does with the printer: set it up, and print to it. */
interface PrinterRepository {

    val settings: StateFlow<PrinterSettings>

    fun updateSettings(transform: (PrinterSettings) -> PrinterSettings)

    /** Paired Bluetooth devices the operator can choose from. */
    fun pairedPrinters(): Result<List<BluetoothPrinter>>

    fun isRawBtInstalled(): Boolean

    suspend fun print(document: PrintDocument, copies: Int = 1): Result<PrintResult>
}

@Singleton
class PrinterRepositoryImpl @Inject constructor(
    private val store: PrinterSettingsStore,
    private val bluetooth: BluetoothPrinters,
    private val rawBt: RawBtBridge,
) : PrinterRepository {

    private val renderer = PrintRenderer()

    /**
     * One job on the wire at a time. Two jobs sharing a printer interleave
     * their bytes into one garbled print — an auto-print firing while a slip is
     * still going out would do exactly that.
     */
    private val printerLock = Mutex()

    override val settings: StateFlow<PrinterSettings> = store.settings

    override fun updateSettings(transform: (PrinterSettings) -> PrinterSettings) = store.update(transform)

    override fun pairedPrinters(): Result<List<BluetoothPrinter>> = try {
        Result.success(bluetooth.pairedPrinters())
    } catch (error: PrinterException) {
        Result.failure(error)
    }

    override fun isRawBtInstalled(): Boolean = rawBt.isInstalled()

    override suspend fun print(document: PrintDocument, copies: Int): Result<PrintResult> = try {
        val current = settings.value
        val payload = renderer.render(document, current, copies)
        val transport = transportFor(current)
        printerLock.withLock {
            // Once bytes start going out, finish: a job cut off mid-stream leaves
            // the printer half-way through a command and prints junk next time.
            withContext(NonCancellable) { transport.send(payload) }
        }.let { Result.success(it) }
    } catch (error: PrinterException) {
        Result.failure(error)
    } catch (error: SecurityException) {
        // A Bluetooth permission revoked from settings while the app was open.
        Result.failure(PrinterException.PermissionMissing())
    }

    private fun transportFor(settings: PrinterSettings): PrinterTransport = when (settings.connection) {
        PrinterConnection.RAWBT -> rawBt
        PrinterConnection.BLUETOOTH -> settings.bluetoothAddress?.takeIf { it.isNotBlank() }
            ?.let(bluetooth::transportFor)
            ?: detectBuiltInPrinter()
    }

    /**
     * First print on a fresh install: find the handheld's own printer so the
     * operator never has to visit Printer settings.
     *
     * The built-in printer is exposed either as a paired virtual Bluetooth
     * device (preferred — it reports success) or only through the vendor's
     * RawBT service. A detected Bluetooth printer is saved; the RawBT fallback
     * is not, so pairing the printer later still upgrades to the direct path.
     */
    private fun detectBuiltInPrinter(): PrinterTransport {
        val scan = runCatching { bluetooth.pairedPrinters() }
        val builtIn = scan.getOrNull()?.filter { it.looksLikePrinter }?.singleOrNull()
        if (builtIn != null) {
            store.update {
                it.copy(
                    connection = PrinterConnection.BLUETOOTH,
                    bluetoothAddress = builtIn.address,
                    bluetoothName = builtIn.name,
                )
            }
            return bluetooth.transportFor(builtIn.address)
        }
        if (rawBt.isInstalled()) return rawBt
        // Say *why* nothing was found when Bluetooth itself was the problem.
        throw scan.exceptionOrNull() as? PrinterException ?: PrinterException.NotConfigured()
    }
}
