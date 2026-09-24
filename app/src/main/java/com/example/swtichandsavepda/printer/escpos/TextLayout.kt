package com.example.swtichandsavepda.printer.escpos

/** Fixed-width text helpers for a monospaced receipt column. */
internal object TextLayout {

    private const val ELLIPSIS = ".."

    /**
     * Word-wraps [text] into lines of at most [width] characters. A word longer
     * than a line is hard-split. With [maxLines], the last kept line is marked
     * with an ellipsis so a truncated name never reads as complete.
     */
    fun wrap(text: String, width: Int, maxLines: Int = Int.MAX_VALUE): List<String> {
        require(width > ELLIPSIS.length) { "width must leave room for an ellipsis" }
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) lines += current.toString()
            current = StringBuilder()
        }

        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
            var remaining = word
            while (remaining.length > width) {
                flush()
                lines += remaining.take(width)
                remaining = remaining.drop(width)
            }
            val needed = if (current.isEmpty()) remaining.length else current.length + 1 + remaining.length
            if (needed > width) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(remaining)
        }
        flush()

        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        val last = kept.last()
        kept[kept.lastIndex] = last.take(width - ELLIPSIS.length).trimEnd() + ELLIPSIS
        return kept
    }

    /** "Total ........ £4.20" style: [left] and [right] pushed to either edge. */
    fun spread(left: String, right: String, width: Int): String {
        val gap = width - left.length - right.length
        return if (gap >= 1) left + " ".repeat(gap) + right else "${left.take(width - right.length - 1)} $right"
    }

    fun divider(width: Int, character: Char = '-'): String = character.toString().repeat(width)
}
