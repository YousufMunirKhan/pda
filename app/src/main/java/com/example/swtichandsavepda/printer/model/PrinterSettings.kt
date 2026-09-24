package com.example.swtichandsavepda.printer.model

/** How print jobs reach the printer. */
enum class PrinterConnection {
    /**
     * Straight to the printer over Bluetooth SPP. On handheld POS units the
     * built-in printer usually shows up as a paired virtual device
     * ("InnerPrinter", "BlueTooth Printer", …).
     */
    BLUETOOTH,

    /**
     * Handed to the RawBT print service, which owns the connection. The
     * fallback for a printer the app cannot reach directly (serial or USB).
     */
    RAWBT,
}

/**
 * The operator's printer set-up. Stored on the device — none of it is sensitive.
 *
 * Sticker rolls without a gap sensor are driven by [labelLengthMm]: the label
 * height plus the gap to the next one, so every label feeds exactly one pitch
 * and they stay aligned across a run.
 */
data class PrinterSettings(
    val connection: PrinterConnection = PrinterConnection.BLUETOOTH,
    val bluetoothAddress: String? = null,
    val bluetoothName: String? = null,
    val labelLengthMm: Int = DEFAULT_LABEL_LENGTH_MM,
    /** Feed to the next label with the printer's own sensor (`GS FF`) instead of by length. */
    val useGapSensor: Boolean = false,
    /** Print a label as soon as a scan resolves to a product. */
    val autoPrintOnScan: Boolean = false,
    val labelCopies: Int = 1,
) {
    companion object {
        /** A 30 mm sticker plus a 2 mm gap — the common 58 mm roll. */
        const val DEFAULT_LABEL_LENGTH_MM = 32
        const val MIN_LABEL_LENGTH_MM = 20
        const val MAX_LABEL_LENGTH_MM = 100
        const val MAX_COPIES = 50
    }
}

/** A paired Bluetooth device the operator can pick as the printer. */
data class BluetoothPrinter(
    val name: String,
    val address: String,
    /** Its name or address matches a known built-in / receipt printer pattern. */
    val looksLikePrinter: Boolean,
)

/** How a job left the app. */
enum class PrintResult {
    /** Sent to the printer and the connection closed cleanly. */
    PRINTED,

    /** Passed to RawBT, which prints it — the app gets no confirmation back. */
    HANDED_OFF,
}
