package com.example.swtichandsavepda.presentation.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.swtichandsavepda.scanner.ScanIntents
import com.example.swtichandsavepda.scanner.UrovoScanner

private const val TAG = "HardwareScanner"

/**
 * Bridges the PDA's hardware scan engine (physical trigger) into the app.
 *
 * On the Urovo CT58S the scan service decodes in the background and, in "Intent
 * output" mode, broadcasts the result. This effect registers a lifecycle-scoped
 * receiver for the known scan actions and forwards the decoded value into the
 * same [onBarcode] callback the camera uses — so the trigger and the camera are
 * interchangeable and the ViewModel's re-entry guard covers both.
 *
 * Every broadcast is logged under tag `HardwareScanner`, so the first real scan
 * confirms the device's exact action and extra key (see [ScanIntents]). The
 * receiver lives only while the composable is on screen; leaving the scanner
 * unregisters it.
 */
@Composable
fun HardwareScannerEffect(onBarcode: (String) -> Unit) {
    val context = LocalContext.current
    // Keeps the receiver pointed at the latest callback without re-registering.
    val currentOnBarcode by rememberUpdatedState(onBarcode)

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val extras = intent.stringExtras()
                Log.d(TAG, ScanIntents.describeForLog(intent.action, extras))
                ScanIntents.extractBarcode(extras)?.let(currentOnBarcode)
            }
        }

        val filter = IntentFilter().apply {
            ScanIntents.CANDIDATE_ACTIONS.forEach(::addAction)
        }
        // The scan service is a separate process, so the receiver must be
        // exported to hear it. ContextCompat applies the API 33+ export flag
        // safely on the API 26 floor.
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        onDispose {
            // Unregistering a receiver that failed to register would throw;
            // guard so teardown is never the thing that crashes the screen.
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}

/**
 * Soft-trigger control for the Scan screen: whether a hardware engine is present
 * ([isAvailable]) and a call to fire it ([triggerScan], returning false if the
 * scan could not be started). Decoded results arrive separately via
 * [HardwareScannerEffect].
 */
class PdaScanController(
    val isAvailable: Boolean,
    val triggerScan: () -> Boolean,
)

/**
 * Owns the device's scan engine for as long as the calling screen is composed.
 *
 * The engine is opened on entry and released on exit, and handed back on
 * resume / released on pause so a backgrounded app never holds it from other
 * apps. On non-Urovo hardware [PdaScanController.isAvailable] is false and the
 * caller should offer only the camera.
 */
@Composable
fun rememberPdaScanController(): PdaScanController {
    val scanner = remember { UrovoScanner() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> scanner.open()
                Lifecycle.Event.ON_PAUSE -> scanner.close()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        scanner.open()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            scanner.close()
        }
    }

    return remember(scanner) {
        PdaScanController(
            isAvailable = scanner.isAvailable,
            triggerScan = scanner::startDecode,
        )
    }
}

/**
 * The intent's String-valued extras as a plain map — the decoded barcode is one
 * of them. Non-string extras (byte arrays, symbology ints) are dropped; parsing
 * happens in [ScanIntents].
 */
private fun Intent.stringExtras(): Map<String, String> {
    val bundle = extras ?: return emptyMap()
    val result = LinkedHashMap<String, String>()
    for (key in bundle.keySet()) {
        @Suppress("DEPRECATION") // get(key) is the only untyped accessor across APIs.
        val value = bundle.get(key)
        if (value is String) result[key] = value
    }
    return result
}
