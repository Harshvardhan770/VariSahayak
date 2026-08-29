package com.varisahayak.feature.qr

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX analyzer that reads QR codes.
 *
 * Restricted to [Barcode.FORMAT_QR_CODE]: narrowing the format set measurably speeds up
 * detection, and the app has no use for the product barcodes a volunteer will inevitably
 * point the camera at.
 *
 * The bundled ML Kit model is used rather than the Play-Services-delivered one, so the
 * scanner works the first time it is opened on the route with no connectivity. An
 * unbundled model that has not downloaded yet is a dead scanner at the moment it matters.
 */
class QrAnalyzer(
    private val onQrCode: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    /**
     * Set once a code has been accepted. A camera stream produces the same code dozens of
     * times a second, and without this the screen would fire the same scan repeatedly
     * while the navigation transition is still running.
     */
    private val isPaused = AtomicBoolean(false)

    @SuppressLint("UnsafeOptInUsageError") // ImageProxy.image is experimental but stable
    override fun analyze(imageProxy: ImageProxy) {
        if (isPaused.get()) {
            imageProxy.close()
            return
        }

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
                val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                if (value != null && isPaused.compareAndSet(false, true)) {
                    onQrCode(value)
                }
            }
            // Always close, whatever happened. A leaked ImageProxy stalls the analyser
            // after a few frames and the preview silently freezes.
            .addOnCompleteListener { imageProxy.close() }
    }

    /** Re-arms the analyzer after a rejected or dismissed scan. */
    fun resume() = isPaused.set(false)

    fun close() = scanner.close()
}
