package com.example.swtichandsavepda.scanner

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable

/**
 * Bridges CameraX frames to ML Kit barcode detection.
 *
 * Detection runs continuously, so the same physical label produces a match on
 * every frame. [onBarcodeDetected] is therefore throttled: a given value is
 * reported once, then suppressed until [rescanDelayMillis] has passed, which
 * stops a single scan from firing dozens of repeat lookups.
 */
class BarcodeAnalyzer(
    private val throttle: ScanThrottle = ScanThrottle(),
    private val onBarcodeDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                // Retail/warehouse label formats. Narrowing the set measurably
                // improves detection speed over the all-formats default.
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_QR_CODE,
            )
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { it.rawValue }?.let(::emitIfNew)
            }
            // A failed frame is not actionable — the next frame retries.
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun emitIfNew(value: String) {
        if (throttle.shouldAccept(value)) {
            onBarcodeDetected(value)
        }
    }

    override fun close() {
        scanner.close()
    }
}
