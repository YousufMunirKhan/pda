package com.example.swtichandsavepda.printer.escpos

import java.text.Normalizer

/**
 * Text → printer bytes in code page 437, the ESC/POS default that every 58 mm
 * thermal printer ships with (selected explicitly with `ESC t 0`).
 *
 * Written by hand rather than via `Charset.forName("IBM437")`: that charset is
 * not guaranteed on Android, and an unmappable character must degrade to
 * something readable, not throw or print mojibake. The printers these units
 * carry also boot in a Chinese double-byte mode, where a stray high byte would
 * swallow the next character — so anything outside the table becomes ASCII.
 */
internal object Cp437Encoder {

    private val HIGH_CHARACTERS: Map<Char, Byte> = mapOf(
        '£' to 0x9C.toByte(),
        '€' to 0xEE.toByte(), // CP437 has no euro; ε is the conventional stand-in
        '°' to 0xF8.toByte(),
        '±' to 0xF1.toByte(),
        '½' to 0xAB.toByte(),
        '¼' to 0xAC.toByte(),
        '·' to 0xFA.toByte(),
    )

    /** Punctuation that normalisation leaves alone but CP437 cannot print. */
    private val ASCII_SUBSTITUTES: Map<Char, String> = mapOf(
        '×' to "x",
        '–' to "-",
        '—' to "-",
        '‘' to "'",
        '’' to "'",
        '“' to "\"",
        '”' to "\"",
        '…' to "...",
    )

    fun encode(text: String): ByteArray {
        val output = ArrayList<Byte>(text.length)
        text.forEach { character ->
            when {
                character == '\n' || character.code in PRINTABLE_ASCII -> output += character.code.toByte()
                character in HIGH_CHARACTERS -> output += HIGH_CHARACTERS.getValue(character)
                character in ASCII_SUBSTITUTES ->
                    ASCII_SUBSTITUTES.getValue(character).forEach { output += it.code.toByte() }
                else -> output += asciiFallback(character).code.toByte()
            }
        }
        return output.toByteArray()
    }

    /** "é" → "e": strip the accent; anything with no ASCII base becomes "?". */
    private fun asciiFallback(character: Char): Char {
        val base = Normalizer.normalize(character.toString(), Normalizer.Form.NFD)
            .firstOrNull { it.code in PRINTABLE_ASCII }
        return base ?: '?'
    }

    private val PRINTABLE_ASCII = 0x20..0x7E
}
