package com.example.swtichandsavepda.presentation

import com.example.swtichandsavepda.MainDispatcherRule
import com.example.swtichandsavepda.data.model.AuthState
import com.example.swtichandsavepda.data.model.AuthUser
import com.example.swtichandsavepda.data.model.ProductRef
import com.example.swtichandsavepda.data.repository.PdaAuthRepository
import com.example.swtichandsavepda.printer.PrinterException
import com.example.swtichandsavepda.printer.PrinterRepository
import com.example.swtichandsavepda.printer.model.BluetoothPrinter
import com.example.swtichandsavepda.printer.model.PrintDocument
import com.example.swtichandsavepda.printer.model.PrintResult
import com.example.swtichandsavepda.printer.model.PrinterSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrintViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakePrinterRepository(
        settings: PrinterSettings = PrinterSettings(bluetoothAddress = "00:11:22:33:44:55"),
        var result: Result<PrintResult> = Result.success(PrintResult.PRINTED),
        val gate: CompletableDeferred<Unit>? = null,
    ) : PrinterRepository {
        val printed = mutableListOf<Pair<PrintDocument, Int>>()
        private val state = MutableStateFlow(settings)
        override val settings: StateFlow<PrinterSettings> = state
        override fun updateSettings(transform: (PrinterSettings) -> PrinterSettings) {
            state.value = transform(state.value)
        }
        override fun pairedPrinters(): Result<List<BluetoothPrinter>> = Result.success(emptyList())
        override fun isRawBtInstalled() = false
        override suspend fun print(document: PrintDocument, copies: Int): Result<PrintResult> {
            printed += document to copies
            gate?.await()
            return result
        }
    }

    private class FakeAuthRepository : PdaAuthRepository {
        override val authState: StateFlow<AuthState> =
            MutableStateFlow(AuthState.Authenticated(AuthUser(1, "Ali", "a@b.c", 1), null))
        override suspend fun login(email: String, password: String) = Result.success(authState.value.let {
            (it as AuthState.Authenticated).user
        })
        override suspend fun bootstrap() = Unit
        override suspend fun logout() = Result.success(Unit)
    }

    private val product = ProductRef(
        id = 55, name = "Coke 500ml", barcode = "5012345678900", code = null,
        cost = 1.0, retail = 2.49, unitType = null,
    )

    @Test
    fun `a scan does not print when auto-print is off`() {
        val printer = FakePrinterRepository()
        val viewModel = PrintViewModel(printer, FakeAuthRepository())

        viewModel.onProductScanned(scanId = 1, product = product, unit = null)

        assertTrue(printer.printed.isEmpty())
    }

    @Test
    fun `auto-print prints each scan once, with the default copies, however often it is re-offered`() {
        val printer = FakePrinterRepository(
            PrinterSettings(bluetoothAddress = "x", autoPrintOnScan = true, labelCopies = 2),
        )
        val viewModel = PrintViewModel(printer, FakeAuthRepository())

        viewModel.onProductScanned(scanId = 1, product = product, unit = null)
        viewModel.onProductScanned(scanId = 1, product = product, unit = null) // recomposition
        viewModel.onProductScanned(scanId = 2, product = product, unit = null) // a real rescan

        assertEquals(2, printer.printed.size)
        assertEquals(2, printer.printed.first().second)
    }

    @Test
    fun `a second print while one is out is ignored, not queued`() {
        val gate = CompletableDeferred<Unit>()
        val printer = FakePrinterRepository(gate = gate)
        val viewModel = PrintViewModel(printer, FakeAuthRepository())

        viewModel.printLabel(product, unit = null, copies = 1)
        viewModel.printLabel(product, unit = null, copies = 1)
        assertTrue(viewModel.uiState.value.isPrinting)

        gate.complete(Unit)

        assertEquals(1, printer.printed.size)
        assertNull(viewModel.uiState.value.activeJobKey)
    }

    @Test
    fun `a failure shows the printer's own advice and stays until dismissed`() = runTest {
        val printer = FakePrinterRepository(result = Result.failure(PrinterException.BluetoothOff()))
        val viewModel = PrintViewModel(printer, FakeAuthRepository())

        viewModel.printLabel(product, unit = null, copies = 1)

        val message = viewModel.uiState.value.message!!
        assertTrue(message.isError)
        assertTrue(message.text.contains("Bluetooth is off"))
    }

    @Test
    fun `a product with no barcode or code is reported without touching the printer`() {
        val printer = FakePrinterRepository()
        val viewModel = PrintViewModel(printer, FakeAuthRepository())

        viewModel.printLabel(product.copy(barcode = null, code = null), unit = null, copies = 1)

        assertTrue(printer.printed.isEmpty())
        assertTrue(viewModel.uiState.value.message!!.isError)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PrintViewModelMessageTimingTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun `a success message clears itself after a few seconds`() = runTest(dispatcher) {
        val viewModel = PrintViewModel(
            printerRepository = object : PrinterRepository {
                override val settings: StateFlow<PrinterSettings> = MutableStateFlow(PrinterSettings())
                override fun updateSettings(transform: (PrinterSettings) -> PrinterSettings) = Unit
                override fun pairedPrinters(): Result<List<BluetoothPrinter>> = Result.success(emptyList())
                override fun isRawBtInstalled() = false
                override suspend fun print(document: PrintDocument, copies: Int) = Result.success(PrintResult.PRINTED)
            },
            authRepository = object : PdaAuthRepository {
                override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Unauthenticated)
                override suspend fun login(email: String, password: String) = Result.failure<AuthUser>(IllegalStateException())
                override suspend fun bootstrap() = Unit
                override suspend fun logout() = Result.success(Unit)
            },
        )
        val product = ProductRef(1, "Tea", "5012345678900", null, null, 1.0, null)

        viewModel.printLabel(product, unit = null, copies = 1)
        advanceTimeBy(100)
        assertEquals("Label printed.", viewModel.uiState.value.message?.text)

        advanceTimeBy(5_000)
        assertNull(viewModel.uiState.value.message)
    }
}
