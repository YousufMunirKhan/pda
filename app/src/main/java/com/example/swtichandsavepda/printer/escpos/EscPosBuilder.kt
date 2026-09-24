package com.example.swtichandsavepda.printer.escpos

import java.io.ByteArrayOutputStream

/**
 * A minimal ESC/POS command writer — only the commands a 58 mm thermal printer
 * reliably honours. Pure bytes, no I/O, so every layout is unit-testable.
 *
 * It also tracks how far the paper has advanced ([consumedDots]), which is how
 * a label is padded to exactly one sticker length on printers with no gap
 * sensor.
 */
internal class EscPosBuilder {

    private val output = ByteArrayOutputStream()

    /** Vertical dots advanced since [initialize], as far as the layout knows. */
    var consumedDots: Int = 0
        private set

    private var lineSpacingDots = DEFAULT_LINE_SPACING_DOTS

    /**
     * Resets the printer, leaves the Chinese double-byte mode these units boot
     * in, and selects CP437 so '£' prints as '£'.
     */
    fun initialize() = apply {
        write(ESC, '@'.code)
        write(FS, '.'.code)
        write(ESC, 't'.code, 0)
        setLineSpacing(DEFAULT_LINE_SPACING_DOTS)
        consumedDots = 0
    }

    fun align(alignment: Alignment) = apply { write(ESC, 'a'.code, alignment.code) }

    fun bold(enabled: Boolean) = apply { write(ESC, 'E'.code, if (enabled) 1 else 0) }

    fun size(textSize: TextSize) = apply { write(GS, '!'.code, textSize.code) }

    /** Font A (12 × 24 dots) or the narrower font B (9 × 17). */
    fun font(font: Font) = apply { write(ESC, 'M'.code, font.code) }

    /**
     * Fixes the height of every following text line. Without it the printer's
     * default depends on its firmware, and label heights stop adding up.
     */
    fun setLineSpacing(dots: Int) = apply {
        lineSpacingDots = dots.coerceIn(0, MAX_SINGLE_FEED_DOTS)
        write(ESC, '3'.code, lineSpacingDots)
    }

    /** Prints one line of text and advances by the current line spacing. */
    fun line(text: String = "") = apply {
        output.write(Cp437Encoder.encode(text))
        output.write(LF)
        consumedDots += lineSpacingDots
    }

    /** Advances the paper by exact dots (`ESC J`), splitting feeds over 255. */
    fun feedDots(dots: Int) = apply {
        var remaining = dots
        while (remaining > 0) {
            val step = remaining.coerceAtMost(MAX_SINGLE_FEED_DOTS)
            write(ESC, 'J'.code, step)
            remaining -= step
            consumedDots += step
        }
    }

    /**
     * Prints [spec] with its digits underneath (`GS k`, format 2). [heightDots]
     * is the bar height; the caption adds [BARCODE_CAPTION_DOTS].
     */
    fun barcode(spec: BarcodeSpec, heightDots: Int, moduleWidthDots: Int) = apply {
        write(GS, 'h'.code, heightDots.coerceIn(1, MAX_SINGLE_FEED_DOTS))
        write(GS, 'w'.code, moduleWidthDots)
        write(GS, 'H'.code, HRI_BELOW)
        write(GS, 'f'.code, 0)
        write(GS, 'k'.code, spec.symbology.escPosId, spec.payload.size)
        output.write(spec.payload)
        // Some firmwares leave the head mid-line after GS k; a feed makes the
        // next row start clean on all of them.
        output.write(LF)
        consumedDots += heightDots + BARCODE_CAPTION_DOTS
    }

    /** Label stock with a gap sensor: feed to the start of the next sticker. */
    fun feedToNextLabel() = apply { write(GS, FORM_FEED) }

    fun build(): ByteArray = output.toByteArray()

    private fun write(vararg bytes: Int) {
        bytes.forEach { output.write(it) }
    }

    enum class Alignment(val code: Int) { LEFT(0), CENTER(1), RIGHT(2) }

    enum class TextSize(val code: Int) {
        NORMAL(0x00),
        DOUBLE_HEIGHT(0x01),
        DOUBLE(0x11),
        TRIPLE(0x22),
    }

    enum class Font(val code: Int, val charWidthDots: Int) {
        A(0, 12),
        B(1, 9),
    }

    companion object {
        private const val ESC = 0x1B
        private const val GS = 0x1D
        private const val FS = 0x1C
        private const val LF = 0x0A
        private const val FORM_FEED = 0x0C
        private const val HRI_BELOW = 2
        private const val MAX_SINGLE_FEED_DOTS = 255

        /** Font A is 24 dots tall; 6 dots of leading keeps lines apart. */
        const val DEFAULT_LINE_SPACING_DOTS = 30

        /** The human-readable digits under a barcode, plus its gap above. */
        const val BARCODE_CAPTION_DOTS = 30
    }
}
