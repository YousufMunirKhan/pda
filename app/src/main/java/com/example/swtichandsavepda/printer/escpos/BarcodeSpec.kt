package com.example.swtichandsavepda.printer.escpos

/** The 1D symbologies the app prints, with their ESC/POS `GS k` (format 2) ids. */
enum class Symbology(val escPosId: Int) {
    UPC_A(65),
    EAN_13(67),
    EAN_8(68),
    CODE_128(73),
}

/**
 * A barcode ready for `GS k`: which symbology, the exact payload bytes, and how
 * many modules (narrow bars) wide it will print.
 *
 * Retail codes print as the symbology they already are, so the sticker scans
 * back to the same product at the till. Anything else — internal codes, letters,
 * odd lengths — prints as Code 128.
 */
class BarcodeSpec private constructor(
    val symbology: Symbology,
    val payload: ByteArray,
    val moduleCount: Int,
    /** What the human-readable line under the bars shows. */
    val text: String,
) {
    /**
     * The widest module (in printer dots) that still fits [printableDots],
     * capped at [maxModuleDots]; null when even one dot per module is too wide.
     */
    fun moduleWidthFor(printableDots: Int, maxModuleDots: Int = MAX_MODULE_DOTS): Int? =
        (maxModuleDots downTo 1).firstOrNull { width -> moduleCount * width <= printableDots }

    companion object {
        private const val MAX_MODULE_DOTS = 3

        /** Start (11) + checksum (11) + stop (13) around the data symbols. */
        private const val CODE_128_OVERHEAD_MODULES = 35
        private const val CODE_128_SYMBOL_MODULES = 11

        private const val CODE_SET_PREFIX = '{'.code
        private const val CODE_SET_B = 'B'.code
        private const val CODE_SET_C = 'C'.code

        /** Code set C packs two digits per symbol; worth it from four digits up. */
        private const val MIN_CODE_C_DIGITS = 4

        private val PRINTABLE_ASCII = 0x20..0x7E

        /** Returns null for a blank code or one with nothing printable in it. */
        fun of(rawCode: String): BarcodeSpec? {
            val code = rawCode.trim()
            if (code.isEmpty()) return null
            val isNumeric = code.all(Char::isDigit)

            return when {
                isNumeric && code.length == 13 && hasValidCheckDigit(code) ->
                    retail(Symbology.EAN_13, code, modules = 95)

                isNumeric && code.length == 12 && hasValidCheckDigit(code) ->
                    retail(Symbology.UPC_A, code, modules = 95)

                isNumeric && code.length == 8 && hasValidCheckDigit(code) ->
                    retail(Symbology.EAN_8, code, modules = 67)

                else -> code128(code)
            }
        }

        /**
         * The EAN/UPC family: the printer computes the check digit itself, so
         * the payload is every digit but the last. Sending all of them is
         * rejected by a good share of printers.
         */
        private fun retail(symbology: Symbology, code: String, modules: Int) = BarcodeSpec(
            symbology = symbology,
            payload = code.dropLast(1).toByteArray(Charsets.US_ASCII),
            moduleCount = modules,
            text = code,
        )

        private fun code128(code: String): BarcodeSpec? {
            val printable = code.filter { it.code in PRINTABLE_ASCII }
            if (printable.isEmpty()) return null

            val useCodeSetC = printable.all(Char::isDigit) &&
                printable.length % 2 == 0 &&
                printable.length >= MIN_CODE_C_DIGITS

            return if (useCodeSetC) {
                // Code set C takes binary 0–99 per symbol, not ASCII digits.
                val pairs = printable.chunked(2).map { it.toInt().toByte() }
                BarcodeSpec(
                    symbology = Symbology.CODE_128,
                    payload = byteArrayOf(CODE_SET_PREFIX.toByte(), CODE_SET_C.toByte()) + pairs,
                    moduleCount = pairs.size * CODE_128_SYMBOL_MODULES + CODE_128_OVERHEAD_MODULES,
                    text = printable,
                )
            } else {
                BarcodeSpec(
                    symbology = Symbology.CODE_128,
                    payload = byteArrayOf(CODE_SET_PREFIX.toByte(), CODE_SET_B.toByte()) +
                        printable.toByteArray(Charsets.US_ASCII),
                    moduleCount = printable.length * CODE_128_SYMBOL_MODULES + CODE_128_OVERHEAD_MODULES,
                    text = printable,
                )
            }
        }

        /**
         * GS1 mod-10: weights alternate 3,1 from the digit left of the check
         * digit. The same rule serves EAN-13, UPC-A and EAN-8.
         */
        internal fun hasValidCheckDigit(code: String): Boolean {
            val digits = code.map { it - '0' }
            val body = digits.dropLast(1).reversed()
            val sum = body.withIndex().sumOf { (index, digit) -> if (index % 2 == 0) digit * 3 else digit }
            return (10 - sum % 10) % 10 == digits.last()
        }
    }
}
