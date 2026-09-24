package com.example.swtichandsavepda.printer.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.swtichandsavepda.printer.PrinterException
import com.example.swtichandsavepda.printer.model.BluetoothPrinter
import com.example.swtichandsavepda.printer.model.PrintResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bluetooth Classic (SPP) printing.
 *
 * Handheld POS units expose their built-in thermal printer as a paired virtual
 * device, so the same code drives the built-in printer and any external
 * Bluetooth receipt/label printer.
 */
@Singleton
class AndroidBluetoothPrinters @Inject constructor(
    @ApplicationContext private val context: Context,
) : BluetoothPrinters {

    // Every call is guarded by requireConnectPermission(); lint cannot see that.
    @SuppressLint("MissingPermission")
    override fun pairedPrinters(): List<BluetoothPrinter> {
        val adapter = requireEnabledAdapter()
        return adapter.bondedDevices.orEmpty()
            .map { device ->
                val name = device.name?.takeIf { it.isNotBlank() } ?: device.address
                BluetoothPrinter(
                    name = name,
                    address = device.address,
                    looksLikePrinter = looksLikePrinter(name, device.address),
                )
            }
            .sortedWith(compareByDescending<BluetoothPrinter> { it.looksLikePrinter }.thenBy { it.name })
    }

    override fun transportFor(address: String): PrinterTransport = object : PrinterTransport {
        override suspend fun send(payload: ByteArray): PrintResult = send(address, payload)
    }

    @SuppressLint("MissingPermission")
    private suspend fun send(address: String, payload: ByteArray): PrintResult = withContext(Dispatchers.IO) {
        val adapter = requireEnabledAdapter()
        val device = runCatching { adapter.getRemoteDevice(address) }
            .getOrElse { throw PrinterException.NotConfigured() }

        val socket = connect(device)
        try {
            val stream = socket.outputStream
            payload.asList().chunked(CHUNK_BYTES).forEach { chunk -> stream.write(chunk.toByteArray()) }
            stream.flush()
            // Closing the socket as soon as write() returns can drop the tail of
            // the job on cheap printers whose buffer drains slower than SPP.
            delay(drainDelayMs(payload.size))
            PrintResult.PRINTED
        } catch (writeError: IOException) {
            throw PrinterException.WriteFailed(writeError)
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * Secure RFCOMM first; some built-in printers only accept the insecure
     * variant, so that is the fallback rather than an error.
     */
    @SuppressLint("MissingPermission")
    private fun connect(device: android.bluetooth.BluetoothDevice): BluetoothSocket {
        val attempts = listOf<() -> BluetoothSocket>(
            { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
        )
        var lastError: IOException? = null
        attempts.forEach { open ->
            val socket = try {
                open()
            } catch (error: IOException) {
                lastError = error
                return@forEach
            }
            try {
                socket.connect()
                return socket
            } catch (error: IOException) {
                lastError = error
                runCatching { socket.close() }
            }
        }
        throw PrinterException.ConnectionFailed(lastError)
    }

    private fun requireEnabledAdapter(): BluetoothAdapter {
        requireConnectPermission()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw PrinterException.BluetoothUnavailable()
        if (!adapter.isEnabled) throw PrinterException.BluetoothOff()
        return adapter
    }

    /** Android 12+ gates every paired-device call behind a runtime permission. */
    private fun requireConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) throw PrinterException.PermissionMissing()
    }

    private fun looksLikePrinter(name: String, address: String): Boolean =
        address == BUILT_IN_PRINTER_ADDRESS || PRINTER_NAME_HINTS.any { name.contains(it, ignoreCase = true) }

    /** Roughly the time the printer needs to print what it just buffered. */
    private fun drainDelayMs(payloadBytes: Int): Long =
        (MIN_DRAIN_MS + payloadBytes / BYTES_PER_DRAIN_MS).coerceAtMost(MAX_DRAIN_MS)

    private companion object {
        /** The Serial Port Profile every Bluetooth ESC/POS printer speaks. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** The fixed address most Android POS builds give their inner printer. */
        const val BUILT_IN_PRINTER_ADDRESS = "00:11:22:33:44:55"
        val PRINTER_NAME_HINTS = listOf("print", "inner", "pos", "mpt", "rpp", "thermal", "58")

        const val CHUNK_BYTES = 512
        const val MIN_DRAIN_MS = 300L
        const val MAX_DRAIN_MS = 2_000L
        const val BYTES_PER_DRAIN_MS = 4
    }
}
