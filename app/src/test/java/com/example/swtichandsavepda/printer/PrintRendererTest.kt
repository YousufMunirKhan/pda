package com.example.swtichandsavepda.printer

import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.printer.escpos.PrintRenderer
import com.example.swtichandsavepda.printer.escpos.TextLayout
import com.example.swtichandsavepda.printer.model.LabelTextSize
import com.example.swtichandsavepda.printer.model.PrintDocument
import com.example.swtichandsavepda.printer.model.PrinterSettings
import com.example.swtichandsavepda.printer.model.SlipLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class PrintRendererTest {

    private val renderer = PrintRenderer(zone = ZoneOffset.UTC)

    private val label = PrintDocument.ProductLabel(
        productName = "Coke 500ml",
        barcode = "5012345678900",
        price = 2.49,
    )

    @Test
    fun `a label prints its barcode as EAN-13 and its price with a real pound sign`() {
        val bytes = renderer.render(label, PrinterSettings())

        assertTrue(bytes.containsSequence(GS, 'k'.code, 67, 12))
        assertTrue(bytes.containsSequence(0x9C, '2'.code, '.'.code, '4'.code, '9'.code))
    }

    @Test
    fun `every label feeds exactly one label length, so a run stays aligned to the roll`() {
        val short = renderer.render(label, PrinterSettings(labelLengthMm = 32))
        val long = renderer.render(label, PrinterSettings(labelLengthMm = 40))

        // 8 mm more label = 64 more dots of feed, and nothing else changes.
        assertEquals(64, long.totalFeedDots() - short.totalFeedDots())
    }

    @Test
    fun `copies repeat the label and its feed`() {
        val one = renderer.render(label, PrinterSettings())
        val three = renderer.render(label, PrinterSettings(), copies = 3)

        assertEquals(3, three.countSequence(GS, 'k'.code))
        assertEquals(one.totalFeedDots() * 3, three.totalFeedDots())
    }

    @Test
    fun `with a gap sensor the printer finds the next label itself`() {
        val bytes = renderer.render(label, PrinterSettings(useGapSensor = true))

        assertTrue(bytes.containsSequence(GS, 0x0C))
    }

    @Test
    fun `the printer is taken out of Chinese mode and into code page 437 first`() {
        val bytes = renderer.render(label, PrinterSettings())

        assertTrue(bytes.startsWithSequence(ESC, '@'.code, FS, '.'.code, ESC, 't'.code, 0))
    }

    @Test(expected = PrinterException.NothingToPrint::class)
    fun `a barcode too long for 58 mm paper is refused rather than printed cut off`() {
        renderer.render(label.copy(barcode = "X".repeat(40)), PrinterSettings())
    }

    @Test
    fun `a GRN lists its lines and ends with a scannable reference`() {
        val slip = PrintDocument.GoodsReceivedSlip(
            storeName = "Main Warehouse",
            receiptReference = "GRN-1001",
            orderReference = "PO-77",
            supplierName = "Acme",
            receivedAtEpochMs = 0,
            receivedBy = "Ali",
            note = null,
            status = PortalState.PENDING,
            lines = listOf(SlipLine("Coke 500ml", 24.0, "BOX")),
            printedAtEpochMs = 0,
        )

        val text = renderer.render(slip, PrinterSettings()).toString(Charsets.ISO_8859_1)

        assertTrue(text.contains("GOODS RECEIVED NOTE"))
        assertTrue(text.contains("24 BOX"))
        assertTrue(text.contains("{BGRN-1001"))
    }

    @Test
    fun `a return slip totals the lines when the portal sent no total`() {
        val doc = PrintDocuments.supplierReturn(
            doc = com.example.swtichandsavepda.data.model.PurchaseReturnDoc(
                id = 1, referenceNo = "RET-1", supplierId = 2, supplierName = "Acme",
                returnReason = "Damaged", portalState = PortalState.PENDING, rejectReason = null,
                total = null,
                lines = listOf(
                    com.example.swtichandsavepda.data.model.PurchaseReturnDocLine(
                        productId = 5, productName = "Coke", quantity = 3.0, costPrice = 1.2, reason = null,
                    ),
                ),
            ),
            storeName = null,
            printedAtEpochMs = 0,
        )

        assertEquals(3.6, doc.total, 0.0001)
        val text = renderer.render(doc, PrinterSettings()).toString(Charsets.ISO_8859_1)
        assertTrue(text.contains("SUPPLIER RETURN"))
        assertTrue(text.contains("Pending"))
        assertFalse(text.contains("Draft"))
    }

    @Test
    fun `defaults are a 50 x 30 sticker in normal text`() {
        val defaults = PrinterSettings()

        assertEquals(50, defaults.labelWidthMm)
        assertEquals(32, defaults.labelLengthMm)
        assertEquals(LabelTextSize.NORMAL, defaults.labelTextSize)
    }

    @Test
    fun `a narrower sticker wraps the name to its own width`() {
        val longName = label.copy(productName = "Extra Large Family Size Chocolate Digestive Biscuits")

        val text = renderer.render(longName, PrinterSettings(labelWidthMm = 40)).toString(Charsets.ISO_8859_1)

        // 40 mm less 1 mm each side = 38 mm = 304 dots = 25 font-A characters.
        val nameLines = text.split('\n').filter { it.contains("Extra") || it.contains("Chocolate") }
        assertTrue(nameLines.isNotEmpty())
        assertTrue(nameLines.all { it.takeLastWhile { c -> c.code >= 0x20 }.length <= 25 })
    }

    @Test
    fun `a barcode that fits 50 mm but not 40 mm is refused on the narrow sticker`() {
        val wide = label.copy(barcode = "X".repeat(25))

        renderer.render(wide, PrinterSettings(labelWidthMm = 50))
        val error = runCatching { renderer.render(wide, PrinterSettings(labelWidthMm = 40)) }.exceptionOrNull()

        assertTrue(error is PrinterException.NothingToPrint)
    }

    @Test
    fun `text size changes the fonts sent to the printer`() {
        val small = renderer.render(label, PrinterSettings(labelTextSize = LabelTextSize.SMALL))
        val large = renderer.render(label, PrinterSettings(labelTextSize = LabelTextSize.LARGE))

        assertTrue(small.containsSequence(ESC, 'M'.code, 1)) // font B name
        assertFalse(small.containsSequence(GS, '!'.code, 0x11))
        assertTrue(large.containsSequence(GS, '!'.code, 0x22)) // triple-size price
    }

    @Test
    fun `a price too wide for the sticker steps down a size instead of wrapping`() {
        val pricey = label.copy(price = 1234.56) // "£1234.56" = 8 characters

        val bytes = renderer.render(pricey, PrinterSettings(labelWidthMm = 30, labelTextSize = LabelTextSize.LARGE))

        assertFalse(bytes.containsSequence(GS, '!'.code, 0x22))
        assertTrue(bytes.containsSequence(GS, '!'.code, 0x11))
    }

    @Test
    fun `large text still feeds exactly one label length`() {
        // 40 and 48 mm: both long enough for the barcode's full height, so the
        // extra 8 mm can only go to feed. (At 32 mm large text shortens the bars,
        // and the difference is shared between bars and feed.)
        val large = PrinterSettings(labelTextSize = LabelTextSize.LARGE)
        val short = renderer.render(label, large.copy(labelLengthMm = 40))
        val long = renderer.render(label, large.copy(labelLengthMm = 48))

        assertEquals(64, long.totalFeedDots() - short.totalFeedDots())
    }

    @Test
    fun `long names wrap on words and a truncated line says so`() {
        val lines = TextLayout.wrap("Extra Large Family Size Chocolate Digestive Biscuits", 16, maxLines = 2)

        assertEquals(2, lines.size)
        assertTrue(lines.all { it.length <= 16 })
        assertTrue(lines.last().endsWith(".."))
    }

    @Test
    fun `every width, length and text size advances exactly one label length`() {
        val longName = label.copy(productName = "Extra Large Family Size Chocolate Digestive Biscuits", unitLabel = "Box × 12")
        for (size in LabelTextSize.entries) {
            for (width in listOf(30, 40, 50, 58)) {
                for (length in listOf(25, 32, 40, 60)) {
                    val settings = PrinterSettings(labelWidthMm = width, labelLengthMm = length, labelTextSize = size)
                    val advanced = renderer.render(longName, settings).simulatedAdvanceDots()
                    assertEquals("$size ${width}x$length", length * 8, advanced)
                }
            }
        }
    }

    // ── Byte helpers ───────────────────────────────────────────────────────

    /**
     * Replays the stream and adds up how far the paper moves: each line feed
     * advances by the current `ESC 3` spacing, `ESC J` by its dots, and a
     * barcode by its bar height plus the digits under it.
     */
    private fun ByteArray.simulatedAdvanceDots(): Int {
        var spacing = 30
        var barHeight = 0
        var total = 0
        var index = 0
        fun at(offset: Int) = this[index + offset].toInt() and 0xFF
        while (index < size) {
            val byte = at(0)
            when {
                byte == ESC && at(1) == '3'.code -> { spacing = at(2); index += 3 }
                byte == ESC && at(1) == 'J'.code -> { total += at(2); index += 3 }
                byte == ESC && at(1) == '@'.code -> index += 2
                byte == ESC -> index += 3 // a, E, M, t — one parameter
                byte == FS -> index += 2
                byte == GS && at(1) == 'h'.code -> { barHeight = at(2); index += 3 }
                byte == GS && at(1) == 'k'.code -> {
                    index += 4 + at(3) // GS k m n + n payload bytes
                    total += barHeight + 30
                    if (index < size && at(0) == 0x0A) index++ // the LF that ends the barcode
                }
                byte == GS && at(1) == 0x0C -> index += 2
                byte == GS -> index += 3 // !, w, H, f — one parameter
                byte == 0x0A -> { total += spacing; index++ }
                else -> index++
            }
        }
        return total
    }

    private fun ByteArray.totalFeedDots(): Int {
        var total = 0
        var index = 0
        while (index < size - 2) {
            if (this[index].toInt() == ESC && this[index + 1].toInt() == 'J'.code) {
                total += this[index + 2].toInt() and 0xFF
                index += 3
            } else {
                index++
            }
        }
        return total
    }

    private fun ByteArray.containsSequence(vararg sequence: Int): Boolean = countSequence(*sequence) > 0

    private fun ByteArray.startsWithSequence(vararg sequence: Int): Boolean =
        sequence.indices.all { this[it].toInt() and 0xFF == sequence[it] }

    private fun ByteArray.countSequence(vararg sequence: Int): Int =
        (0..size - sequence.size).count { start ->
            sequence.indices.all { this[start + it].toInt() and 0xFF == sequence[it] }
        }

    private companion object {
        const val ESC = 0x1B
        const val GS = 0x1D
        const val FS = 0x1C
    }
}
