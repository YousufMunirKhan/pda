package com.example.swtichandsavepda.scanner

/**
 * Suppresses repeat detections of the same barcode.
 *
 * Continuous detection matches the same physical label on every frame, so
 * without this a single scan fires dozens of lookups. A value is accepted
 * once, then suppressed until [rescanDelayMillis] has elapsed; a *different*
 * value is always accepted immediately, so scanning two labels back to back
 * is never blocked.
 *
 * Separated from [BarcodeAnalyzer] so the timing rules are unit-testable
 * without an ML Kit client or a camera.
 */
class ScanThrottle(
    private val rescanDelayMillis: Long = DEFAULT_RESCAN_DELAY_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastValue: String? = null
    private var lastAcceptedAt = 0L

    /** True if [value] should be reported now. */
    fun shouldAccept(value: String): Boolean {
        val now = clock()
        val isSuppressedRepeat = value == lastValue &&
            now - lastAcceptedAt < rescanDelayMillis

        if (isSuppressedRepeat) return false

        lastValue = value
        lastAcceptedAt = now
        return true
    }

    fun reset() {
        lastValue = null
        lastAcceptedAt = 0L
    }

    companion object {
        const val DEFAULT_RESCAN_DELAY_MS = 2_000L
    }
}
