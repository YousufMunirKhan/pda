package com.example.swtichandsavepda.printer

import com.example.swtichandsavepda.printer.model.BluetoothPrinter
import com.example.swtichandsavepda.printer.model.PrintDocument
import com.example.swtichandsavepda.printer.model.PrintResult
import com.example.swtichandsavepda.printer.model.PrinterConnection
import com.example.swtichandsavepda.printer.model.PrinterSettings
import com.example.swtichandsavepda.printer.transport.BluetoothPrinters
import com.example.swtichandsavepda.printer.transport.PrinterTransport
import com.example.swtichandsavepda.printer.transport.RawBtBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** First-print detection of the handheld's built-in printer. */
class PrinterRepositoryTest {

    private class FakeStore(initial: PrinterSettings = PrinterSettings()) : PrinterSettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<PrinterSettings> = state
        override fun update(transform: (PrinterSettings) -> PrinterSettings) {
            state.value = transform(state.value)
        }
    }

    private class FakeBluetooth(
        private val paired: () -> List<BluetoothPrinter>,
    ) : BluetoothPrinters {
        val sentTo = mutableListOf<String>()
        override fun pairedPrinters() = paired()
        override fun transportFor(address: String) = object : PrinterTransport {
            override suspend fun send(payload: ByteArray): PrintResult {
                sentTo += address
                return PrintResult.PRINTED
            }
        }
    }

    private class FakeRawBt(private val installed: Boolean) : RawBtBridge {
        var sends = 0
        override fun isInstalled() = installed
        override suspend fun send(payload: ByteArray): PrintResult {
            sends++
            return PrintResult.HANDED_OFF
        }
    }

    private val innerPrinter = BluetoothPrinter("InnerPrinter", "00:11:22:33:44:55", looksLikePrinter = true)
    private val headset = BluetoothPrinter("Buds", "AA:BB:CC:DD:EE:FF", looksLikePrinter = false)
    private val testPage = PrintDocument.TestPage("auto", 0)

    @Test
    fun `the built-in printer is found and remembered on the very first print`() = runTest {
        val store = FakeStore()
        val bluetooth = FakeBluetooth { listOf(headset, innerPrinter) }
        val repository = PrinterRepositoryImpl(store, bluetooth, FakeRawBt(installed = true))

        val result = repository.print(testPage)

        assertEquals(PrintResult.PRINTED, result.getOrNull())
        assertEquals(listOf(innerPrinter.address), bluetooth.sentTo)
        assertEquals(innerPrinter.address, store.settings.value.bluetoothAddress)
    }

    @Test
    fun `with no printer on Bluetooth the job goes through RawBT, without locking that choice in`() = runTest {
        val store = FakeStore()
        val rawBt = FakeRawBt(installed = true)
        val repository = PrinterRepositoryImpl(store, FakeBluetooth { listOf(headset) }, rawBt)

        val result = repository.print(testPage)

        assertEquals(PrintResult.HANDED_OFF, result.getOrNull())
        assertEquals(1, rawBt.sends)
        assertEquals(PrinterConnection.BLUETOOTH, store.settings.value.connection)
        assertNull(store.settings.value.bluetoothAddress)
    }

    @Test
    fun `when nothing can be found the operator is told why`() = runTest {
        val repository = PrinterRepositoryImpl(
            FakeStore(),
            FakeBluetooth { throw PrinterException.BluetoothOff() },
            FakeRawBt(installed = false),
        )

        val error = repository.print(testPage).exceptionOrNull()

        assertTrue(error is PrinterException.BluetoothOff)
    }

    @Test
    fun `a chosen printer is used as is, with no detection`() = runTest {
        val bluetooth = FakeBluetooth { error("must not scan") }
        val repository = PrinterRepositoryImpl(
            FakeStore(PrinterSettings(bluetoothAddress = "11:22:33:44:55:66")),
            bluetooth,
            FakeRawBt(installed = true),
        )

        repository.print(testPage)

        assertEquals(listOf("11:22:33:44:55:66"), bluetooth.sentTo)
    }
}
