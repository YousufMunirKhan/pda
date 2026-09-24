package com.example.swtichandsavepda.printer

/**
 * Why a print job did not go out. Every message is written for the operator
 * holding the device — it says what to do, not what broke internally.
 */
sealed class PrinterException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class NotConfigured : PrinterException(
        "No printer selected. Open Printer settings and choose the printer.",
    )

    class BluetoothUnavailable : PrinterException("This device has no Bluetooth.")

    class BluetoothOff : PrinterException("Bluetooth is off. Turn it on and try again.")

    class PermissionMissing : PrinterException(
        "The app needs Bluetooth access to print. Allow it in Printer settings.",
    )

    class ConnectionFailed(cause: Throwable?) : PrinterException(
        "Couldn't connect to the printer. Check it is on and has paper, then try again.",
        cause,
    )

    class WriteFailed(cause: Throwable?) : PrinterException(
        "The printer stopped responding mid-print. Check the paper and try again.",
        cause,
    )

    class RawBtMissing : PrinterException(
        "RawBT is not installed. Install it or switch to Bluetooth in Printer settings.",
    )

    class NothingToPrint(reason: String) : PrinterException(reason)
}
