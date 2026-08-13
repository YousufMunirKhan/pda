package com.example.swtichandsavepda.scanner

/**
 * Reads a decoded barcode out of a hardware scan broadcast.
 *
 * Rugged PDAs (the Urovo CT58S this app targets, plus most Zebra/Newland/
 * Honeywell engines) decode in a background service and, in "Intent output"
 * mode, broadcast the result. The action and the extra key carrying the text
 * are configurable in the device's ScanSettings and vary by firmware, so this
 * layer listens on the known candidates and pulls the payload out defensively.
 *
 * Kept free of any Android `Intent`/`Context` types so the parsing rules stay
 * unit-testable on a plain JVM — the receiver in `HardwareScannerEffect` does
 * the framework-side work and hands the extras here as a plain map, mirroring
 * how [ScanThrottle] is separated from [BarcodeAnalyzer].
 */
object ScanIntents {

    /**
     * Broadcast actions rugged scan services emit in "Intent output" mode, in
     * the order we prefer to recognise them. The Urovo CT58S ships with
     * `android.intent.ACTION_DECODE_DATA` by default, but the action is
     * user-configurable, so we register for all of these and let the first real
     * scan confirm which one fires (see [describeForLog]).
     */
    val CANDIDATE_ACTIONS: List<String> = listOf(
        "android.intent.ACTION_DECODE_DATA",           // Urovo (factory default)
        "urovo.rcv.message",                           // Urovo (newer firmware)
        "com.android.server.scannerservice.broadcast", // some Urovo/AOSP builds
        "scan.rcv.message",                            // generic scanner service
        "nlscan.action.SCANNER_RESULT",                // Newland engines
    )

    /**
     * Extra keys the decoded text arrives under, highest confidence first. The
     * first non-blank match wins, so a device using a known key never depends on
     * the [heuristic fallback][extractBarcode].
     */
    val BARCODE_EXTRA_KEYS: List<String> = listOf(
        "barcode_string",  // Urovo ACTION_DECODE_DATA
        "SCAN_BARCODE1",   // some AOSP scanner builds
        "scannerdata",     // com.android.server.scannerservice.broadcast
        "data",
        "value",
        "barcode",
    )

    /**
     * Substrings marking an extra as metadata (symbology, length, aim id…)
     * rather than the payload. Only consulted by the heuristic fallback.
     */
    private val METADATA_HINTS: List<String> =
        listOf("type", "length", "len", "count", "aim", "id", "time")

    /** Shortest string the fallback will treat as a plausible barcode. */
    private const val MIN_FALLBACK_LENGTH = 3

    /**
     * The decoded barcode from a broadcast's string extras, or null if none of
     * them look like a payload.
     *
     * 1. A known [BARCODE_EXTRA_KEYS] entry, if present — deterministic.
     * 2. Otherwise the longest non-metadata string extra, so an unrecognised
     *    firmware still resolves on the very first scan while we read the log to
     *    pin its exact key.
     */
    fun extractBarcode(extras: Map<String, String>): String? {
        for (key in BARCODE_EXTRA_KEYS) {
            val known = extras[key]?.trim()
            if (!known.isNullOrBlank()) return known
        }

        return extras.entries
            .asSequence()
            .filterNot { (key, _) -> key.isMetadata() }
            .map { it.value.trim() }
            .filter { it.length >= MIN_FALLBACK_LENGTH }
            .maxByOrNull { it.length }
    }

    /**
     * Human-readable dump of a received broadcast, so the first hardware scan on
     * a new device reveals its exact action and extra keys in logcat.
     */
    fun describeForLog(action: String?, extras: Map<String, String>): String {
        val body = if (extras.isEmpty()) {
            "    (no string extras)"
        } else {
            extras.entries.joinToString("\n") { (key, value) -> "    • $key = \"$value\"" }
        }
        return "Hardware scan broadcast:\n  action = $action\n  string extras:\n$body"
    }

    private fun String.isMetadata(): Boolean {
        val lower = lowercase()
        return METADATA_HINTS.any { lower.contains(it) }
    }
}
