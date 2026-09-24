package com.example.swtichandsavepda.printer.transport

import com.example.swtichandsavepda.printer.model.BluetoothPrinter
import com.example.swtichandsavepda.printer.model.PrintResult

/** Moves rendered ESC/POS bytes to paper. Implementations throw [com.example.swtichandsavepda.printer.PrinterException]. */
interface PrinterTransport {
    suspend fun send(payload: ByteArray): PrintResult
}

/** The paired-device side of Bluetooth printing. */
interface BluetoothPrinters {

    /** Paired devices, likeliest printers first. Throws when Bluetooth cannot be read. */
    fun pairedPrinters(): List<BluetoothPrinter>

    /** A transport bound to one paired device. */
    fun transportFor(address: String): PrinterTransport
}

/** The RawBT print service, when the device has it. */
interface RawBtBridge : PrinterTransport {
    fun isInstalled(): Boolean
}
