package com.example.swtichandsavepda.printer.escpos

import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.printer.PrinterException
import com.example.swtichandsavepda.printer.escpos.EscPosBuilder.Alignment
import com.example.swtichandsavepda.printer.escpos.EscPosBuilder.TextSize
import com.example.swtichandsavepda.printer.model.LabelTextSize
import com.example.swtichandsavepda.printer.model.PrintDocument
import com.example.swtichandsavepda.printer.model.PrinterSettings
import com.example.swtichandsavepda.printer.model.SlipLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lays out a [PrintDocument] as ESC/POS bytes for a 58 mm, 203 dpi printer:
 * 384 printable dots, 32 characters of font A per line, 8 dots per millimetre.
 */
internal class PrintRenderer(zone: ZoneId = ZoneId.systemDefault()) {

    private val timestamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.UK).withZone(zone)

    fun render(document: PrintDocument, settings: PrinterSettings, copies: Int = 1): ByteArray {
        val builder = EscPosBuilder().initialize()
        repeat(copies.coerceIn(1, PrinterSettings.MAX_COPIES)) {
            when (document) {
                is PrintDocument.ProductLabel -> renderLabel(builder, document, settings)
                is PrintDocument.GoodsReceivedSlip -> renderGoodsReceived(builder, document)
                is PrintDocument.SupplierReturnSlip -> renderSupplierReturn(builder, document)
                is PrintDocument.TestPage -> renderTestPage(builder, document)
            }
        }
        return builder.build()
    }

    // ── Label ───────────────────────────────────────────────────────────────

    /**
     * One sticker, padded to exactly [PrinterSettings.labelLengthMm] so a run of
     * labels stays registered to the roll, and no wider than the sticker.
     *
     * Everything is centred: stickers sit in the middle of the roll and so does
     * the print head, so centred content lands on the sticker at any width with
     * no margin commands the printer might not support.
     *
     * When the text would crowd out the barcode, detail is shed in order of
     * least use: the unit line, then the second name line, then the price. The
     * barcode is the point of the label.
     */
    private fun renderLabel(
        builder: EscPosBuilder,
        label: PrintDocument.ProductLabel,
        settings: PrinterSettings,
    ) {
        val areaDots = labelAreaDots(settings.labelWidthMm)
        val spec = BarcodeSpec.of(label.barcode)
            ?: throw PrinterException.NothingToPrint("This product has no barcode to print.")
        val moduleWidth = spec.moduleWidthFor(areaDots)
            ?: throw PrinterException.NothingToPrint(
                "This barcode is too long for a ${settings.labelWidthMm} mm label. Use a wider label.",
            )

        val style = LabelStyle.of(settings.labelTextSize)
        val pitchDots = settings.labelLengthMm * DOTS_PER_MM
        val plan = planLabel(label, pitchDots, areaDots, style)
        val startDots = builder.consumedDots

        builder.feedDots(LABEL_MARGIN_DOTS).align(Alignment.CENTER)

        builder.font(style.nameFont).size(style.nameSize).bold(true).setLineSpacing(style.nameLineDots)
        plan.nameLines.forEach { builder.line(it) }
        builder.size(TextSize.NORMAL).bold(false)

        plan.unitLine?.let { unit -> builder.setLineSpacing(style.detailLineDots).line(unit) }

        plan.price?.let { price ->
            builder.font(EscPosBuilder.Font.A).size(price.size).bold(true).setLineSpacing(price.lineDots)
                .line(price.text)
                .size(TextSize.NORMAL).bold(false)
        }

        builder.font(EscPosBuilder.Font.A)
            .setLineSpacing(EscPosBuilder.DEFAULT_LINE_SPACING_DOTS)
            .barcode(spec, plan.barHeightDots, moduleWidth)
            .align(Alignment.LEFT)

        if (settings.useGapSensor) {
            builder.feedToNextLabel()
        } else {
            val used = builder.consumedDots - startDots
            builder.feedDots(pitchDots - used)
        }
    }

    /**
     * The printable width for a sticker [widthMm] wide: the sticker less a
     * millimetre of safety each side, capped at what the head can reach.
     */
    private fun labelAreaDots(widthMm: Int): Int =
        ((widthMm - 2 * LABEL_SIDE_SAFETY_MM) * DOTS_PER_MM).coerceIn(MIN_AREA_DOTS, PRINTABLE_DOTS)

    /** One printed size: its ESC/POS size, how wide a character is and how tall a line. */
    private data class TextStyle(val size: TextSize, val charDots: Int, val lineDots: Int)

    /**
     * What each [LabelTextSize] means on paper. The price is the largest text on
     * the label because it is what a shopper reads first.
     */
    private data class LabelStyle(
        val nameFont: EscPosBuilder.Font,
        val nameSize: TextSize,
        val nameLineDots: Int,
        val detailLineDots: Int,
        /** Biggest first; a price too wide for the sticker steps down a size. */
        val priceStyles: List<TextStyle>,
    ) {
        val nameCharDots: Int get() = nameFont.charWidthDots

        companion object {
            private val PRICE_TRIPLE = TextStyle(TextSize.TRIPLE, charDots = 36, lineDots = 78)
            private val PRICE_DOUBLE = TextStyle(TextSize.DOUBLE, charDots = 24, lineDots = 54)
            private val PRICE_NORMAL = TextStyle(TextSize.NORMAL, charDots = 12, lineDots = 30)

            fun of(size: LabelTextSize): LabelStyle = when (size) {
                LabelTextSize.SMALL -> LabelStyle(
                    nameFont = EscPosBuilder.Font.B,
                    nameSize = TextSize.NORMAL,
                    nameLineDots = 22,
                    detailLineDots = 22,
                    priceStyles = listOf(PRICE_NORMAL),
                )

                LabelTextSize.NORMAL -> LabelStyle(
                    nameFont = EscPosBuilder.Font.A,
                    nameSize = TextSize.NORMAL,
                    nameLineDots = 30,
                    detailLineDots = 30,
                    priceStyles = listOf(PRICE_DOUBLE, PRICE_NORMAL),
                )

                LabelTextSize.LARGE -> LabelStyle(
                    nameFont = EscPosBuilder.Font.A,
                    nameSize = TextSize.DOUBLE_HEIGHT,
                    nameLineDots = 54,
                    detailLineDots = 30,
                    priceStyles = listOf(PRICE_TRIPLE, PRICE_DOUBLE, PRICE_NORMAL),
                )
            }
        }
    }

    private data class PriceLine(val text: String, val size: TextSize, val lineDots: Int)

    private data class LabelPlan(
        val nameLines: List<String>,
        val unitLine: String?,
        val price: PriceLine?,
        val barHeightDots: Int = 0,
    )

    private fun planLabel(
        label: PrintDocument.ProductLabel,
        pitchDots: Int,
        areaDots: Int,
        style: LabelStyle,
    ): LabelPlan {
        val nameChars = areaDots / style.nameCharDots
        val detailChars = areaDots / style.nameFont.charWidthDots
        val name = TextLayout.wrap(label.productName, nameChars, maxLines = 2)
        val unit = label.unitLabel?.takeIf { it.isNotBlank() }?.let { TextLayout.wrap(it, detailChars, 1).first() }
        val price = label.price?.let(::money)?.let { text ->
            // A price that wraps would print half on the next line; step down instead.
            val fitting = style.priceStyles.firstOrNull { text.length * it.charDots <= areaDots }
                ?: style.priceStyles.last()
            PriceLine(text, fitting.size, fitting.lineDots)
        }

        val candidates = listOf(
            LabelPlan(name, unit, price),
            LabelPlan(name, null, price),
            LabelPlan(name.take(1), null, price),
            LabelPlan(name.take(1), null, null),
        )
        return candidates.firstNotNullOfOrNull { plan ->
            barHeightFor(plan, pitchDots, style).takeIf { it >= MIN_BAR_DOTS }?.let { plan.copy(barHeightDots = it) }
        } ?: candidates.last().copy(barHeightDots = MIN_BAR_DOTS)
    }

    private fun barHeightFor(plan: LabelPlan, pitchDots: Int, style: LabelStyle): Int {
        val fixed = LABEL_MARGIN_DOTS * 2 +
            plan.nameLines.size * style.nameLineDots +
            (if (plan.unitLine != null) style.detailLineDots else 0) +
            (plan.price?.lineDots ?: 0) +
            EscPosBuilder.BARCODE_CAPTION_DOTS
        return (pitchDots - fixed).coerceAtMost(MAX_BAR_DOTS)
    }

    // ── Slips ───────────────────────────────────────────────────────────────

    private fun renderGoodsReceived(builder: EscPosBuilder, slip: PrintDocument.GoodsReceivedSlip) {
        header(builder, slip.storeName, "GOODS RECEIVED NOTE")
        keyValue(builder, "GRN", slip.receiptReference)
        keyValue(builder, "PO", slip.orderReference)
        slip.supplierName?.let { keyValue(builder, "Supplier", it) }
        slip.receivedAtEpochMs?.let { keyValue(builder, "Received", formatTime(it)) }
        slip.receivedBy?.let { keyValue(builder, "By", it) }
        keyValue(builder, "Status", statusLabel(slip.status))
        builder.line(TextLayout.divider(LINE_CHARS))

        builder.bold(true).line(TextLayout.spread("Item", "Qty", LINE_CHARS)).bold(false)
        slip.lines.forEach { line -> quantityRow(builder, line) }
        builder.line(TextLayout.divider(LINE_CHARS))
        builder.bold(true)
            .line(TextLayout.spread("${slip.lines.size} line(s)", "Total ${pretty(slip.lines.sumOf { it.quantity })}", LINE_CHARS))
            .bold(false)

        slip.note?.takeIf { it.isNotBlank() }?.let { note ->
            builder.line()
            TextLayout.wrap("Note: $note", LINE_CHARS).forEach { builder.line(it) }
        }
        footer(builder, slip.receiptReference, slip.printedAtEpochMs, signatureLabel = "Checked by")
    }

    private fun renderSupplierReturn(builder: EscPosBuilder, slip: PrintDocument.SupplierReturnSlip) {
        header(builder, slip.storeName, "SUPPLIER RETURN")
        keyValue(builder, "Ref", slip.referenceNo)
        slip.supplierName?.let { keyValue(builder, "Supplier", it) }
        slip.orderReference?.let { keyValue(builder, "PO", it) }
        slip.reason?.takeIf { it.isNotBlank() }?.let { keyValue(builder, "Reason", it) }
        keyValue(builder, "Status", statusLabel(slip.status))
        builder.line(TextLayout.divider(LINE_CHARS))

        slip.lines.forEach { line ->
            TextLayout.wrap(line.name, LINE_CHARS).forEach { builder.line(it) }
            val quantity = "  ${quantityLabel(line)}" + (line.unitPrice?.let { " x ${money(it)}" } ?: "")
            builder.line(TextLayout.spread(quantity, line.lineTotal?.let(::money).orEmpty(), LINE_CHARS))
        }
        builder.line(TextLayout.divider(LINE_CHARS))
        builder.bold(true).line(TextLayout.spread("TOTAL", money(slip.total), LINE_CHARS)).bold(false)

        footer(builder, slip.referenceNo, slip.printedAtEpochMs, signatureLabel = "Driver")
    }

    private fun renderTestPage(builder: EscPosBuilder, page: PrintDocument.TestPage) {
        header(builder, storeName = null, title = "PRINTER TEST")
        keyValue(builder, "Via", page.connectionLabel)
        keyValue(builder, "Printed", formatTime(page.printedAtEpochMs))
        keyValue(builder, "Pound sign", money(1.99))
        builder.line()

        builder.align(Alignment.CENTER)
        listOf(SAMPLE_EAN_13, SAMPLE_CODE_128).forEach { code ->
            val spec = BarcodeSpec.of(code) ?: return@forEach
            spec.moduleWidthFor(PRINTABLE_DOTS, maxModuleDots = SLIP_MODULE_DOTS)?.let { width ->
                builder.barcode(spec, SLIP_BAR_DOTS, width).line()
            }
        }
        builder.align(Alignment.LEFT)
        TextLayout.wrap("If both barcodes scan, the printer is ready.", LINE_CHARS).forEach { builder.line(it) }
        builder.feedDots(TEAR_OFF_FEED_DOTS)
    }

    private fun header(builder: EscPosBuilder, storeName: String?, title: String) {
        builder.align(Alignment.CENTER)
        storeName?.takeIf { it.isNotBlank() }?.let { name ->
            builder.size(TextSize.DOUBLE).bold(true).setLineSpacing(DOUBLE_LINE_DOTS)
            TextLayout.wrap(name, DOUBLE_LINE_CHARS, maxLines = 2).forEach { builder.line(it) }
            builder.size(TextSize.NORMAL).setLineSpacing(EscPosBuilder.DEFAULT_LINE_SPACING_DOTS)
        }
        builder.bold(true).line(title).bold(false)
            .align(Alignment.LEFT)
            .line(TextLayout.divider(LINE_CHARS))
    }

    /** Barcode of the document reference, so the paper scans back to the record. */
    private fun footer(builder: EscPosBuilder, reference: String, printedAtEpochMs: Long, signatureLabel: String) {
        builder.line()
        BarcodeSpec.of(reference)?.let { spec ->
            spec.moduleWidthFor(PRINTABLE_DOTS, maxModuleDots = SLIP_MODULE_DOTS)?.let { width ->
                builder.align(Alignment.CENTER).barcode(spec, SLIP_BAR_DOTS, width).align(Alignment.LEFT)
            }
        }
        builder.line()
            .line("$signatureLabel: ____________________".take(LINE_CHARS))
            .line()
            .align(Alignment.CENTER)
            .line("Printed ${formatTime(printedAtEpochMs)}")
            .align(Alignment.LEFT)
            .feedDots(TEAR_OFF_FEED_DOTS)
    }

    /** "Supplier  Acme Wholesale" with the value wrapped under itself. */
    private fun keyValue(builder: EscPosBuilder, key: String, value: String) {
        val valueWidth = LINE_CHARS - KEY_COLUMN_CHARS
        TextLayout.wrap(value, valueWidth).ifEmpty { listOf("") }.forEachIndexed { index, part ->
            val prefix = if (index == 0) key.padEnd(KEY_COLUMN_CHARS) else " ".repeat(KEY_COLUMN_CHARS)
            builder.line(prefix + part)
        }
    }

    /** Name on the left, quantity pinned right; long names continue underneath. */
    private fun quantityRow(builder: EscPosBuilder, line: SlipLine) {
        val quantity = quantityLabel(line)
        val nameWidth = LINE_CHARS - quantity.length - 1
        val nameLines = TextLayout.wrap(line.name, nameWidth.coerceAtLeast(MIN_NAME_CHARS))
        builder.line(TextLayout.spread(nameLines.firstOrNull().orEmpty(), quantity, LINE_CHARS))
        nameLines.drop(1).forEach { builder.line(it) }
    }

    private fun quantityLabel(line: SlipLine): String =
        pretty(line.quantity) + (line.unitCode?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")

    private fun pretty(quantity: Double): String = UomMath.pretty(quantity)

    private fun money(amount: Double): String = String.format(Locale.UK, "£%.2f", amount)

    private fun formatTime(epochMs: Long): String = timestamp.format(Instant.ofEpochMilli(epochMs))

    private fun statusLabel(state: PortalState): String = when (state) {
        PortalState.PENDING -> "Pending"
        PortalState.CONFIRMED -> "Confirmed"
        PortalState.REJECTED -> "Rejected"
        PortalState.UNKNOWN -> "Draft"
    }

    companion object {
        const val PRINTABLE_DOTS = 384
        const val DOTS_PER_MM = 8
        const val LINE_CHARS = 32
        private const val DOUBLE_LINE_CHARS = 16
        private const val DOUBLE_LINE_DOTS = 54
        private const val KEY_COLUMN_CHARS = 10
        private const val MIN_NAME_CHARS = 8

        private const val LABEL_MARGIN_DOTS = 12
        private const val LABEL_SIDE_SAFETY_MM = 1
        private const val MIN_AREA_DOTS = 160
        private const val MIN_BAR_DOTS = 32
        private const val MAX_BAR_DOTS = 80

        private const val SLIP_BAR_DOTS = 60
        private const val SLIP_MODULE_DOTS = 2
        private const val TEAR_OFF_FEED_DOTS = 120

        const val SAMPLE_EAN_13 = "5012345678900"
        private const val SAMPLE_CODE_128 = "SWITCH-SAVE"
    }
}
