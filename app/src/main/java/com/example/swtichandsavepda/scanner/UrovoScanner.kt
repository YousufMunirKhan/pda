package com.example.swtichandsavepda.scanner

import android.util.Log

/**
 * Programmatic control of a Urovo device's hardware scan engine, via reflection
 * over the on-device scanner framework class.
 *
 * Urovo has shipped two SDK families across its range, so a single client fleet
 * can contain both:
 *  - `android.device.ScanManager` — current line (CT40/CT58/DT-series and newer)
 *  - `android.device.ScanDevice`  — older models
 *
 * The two expose the same capability under different method names, so we probe
 * for each in turn and adopt the first one present. On non-Urovo hardware —
 * phones, emulators, CI — neither class exists, [isAvailable] is false, and
 * every call is a safe no-op so callers fall back to the camera.
 *
 * Decoded values are **not** returned here. The engine broadcasts them as
 * `android.intent.ACTION_DECODE_DATA` (or a sibling action), which
 * `HardwareScannerEffect` receives via [ScanIntents] — the same path the
 * physical trigger uses. This class only powers the engine and fires a soft
 * trigger, so an on-screen button can start a scan.
 */
class UrovoScanner {

    /** A resolved engine: its instance plus the method names to drive it. */
    private class Engine(
        val instance: Any,
        val open: String,
        val start: String,
        val stop: String,
        val close: String,
    )

    private val engine: Engine? = PROFILES.firstNotNullOfOrNull { profile ->
        runCatching {
            val instance = Class.forName(profile.className)
                .getDeclaredConstructor()
                .newInstance()
            Log.i(TAG, "Scan engine: ${profile.className}")
            Engine(instance, profile.open, profile.start, profile.stop, profile.close)
        }.getOrNull()
    }.also {
        if (it == null) Log.d(TAG, "No Urovo scan engine on this device — camera only.")
    }

    /** True only on a device that exposes a Urovo scan framework. */
    val isAvailable: Boolean get() = engine != null

    /** Powers on the engine. Safe to call repeatedly. */
    fun open(): Boolean = engine?.let { invoke(it.instance, it.open) } ?: false

    /**
     * Soft trigger — fires the laser/imager once, exactly as the hardware key
     * would. Returns false if there is no engine or the framework rejected the
     * call, so the UI can tell the operator the scan did not start.
     */
    fun startDecode(): Boolean = engine?.let { invoke(it.instance, it.start) } ?: false

    /** Cancels an in-flight decode. */
    fun stopDecode(): Boolean = engine?.let { invoke(it.instance, it.stop) } ?: false

    /** Releases the engine so other apps can use it. */
    fun close(): Boolean = engine?.let { invoke(it.instance, it.close) } ?: false

    /**
     * Calls [method] reflectively. Returns the framework's own boolean result
     * when it returns one, true for a void method that ran, and false if the
     * call threw.
     */
    private fun invoke(target: Any, method: String): Boolean =
        runCatching {
            val result = target.javaClass.getMethod(method).invoke(target)
            (result as? Boolean) ?: true
        }.onFailure { Log.w(TAG, "$method() failed", it) }
            .getOrDefault(false)

    /** Method-name mapping for one SDK family. */
    private class Profile(
        val className: String,
        val open: String,
        val start: String,
        val stop: String,
        val close: String,
    )

    private companion object {
        const val TAG = "UrovoScanner"

        // Probed in order; the first class present on the device wins.
        val PROFILES = listOf(
            Profile(
                className = "android.device.ScanManager",
                open = "openScanner",
                start = "startDecode",
                stop = "stopDecode",
                close = "closeScanner",
            ),
            Profile(
                className = "android.device.ScanDevice",
                open = "open",
                start = "startScan",
                stop = "stopScan",
                close = "close",
            ),
        )
    }
}
