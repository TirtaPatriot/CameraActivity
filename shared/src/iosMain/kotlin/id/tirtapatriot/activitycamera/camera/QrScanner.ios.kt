@file:OptIn(ExperimentalForeignApi::class)

package id.tirtapatriot.activitycamera.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private val METADATA_TYPE_QR: String = AVMetadataObjectTypeQRCode ?: "org.iso.QRCode"

@Composable
actual fun QrScannerView(modifier: Modifier, onScanned: (String) -> Unit) {
    val currentOnScanned by rememberUpdatedState(onScanned)
    val scanner = remember { IosQrScanner { currentOnScanned(it) } }

    LaunchedEffect(scanner) { scanner.start() }
    DisposableEffect(scanner) { onDispose { scanner.stop() } }

    UIKitView(
        factory = { PreviewView(scanner.session) },
        modifier = modifier,
    )
}

private class IosQrScanner(private val onScanned: (String) -> Unit) {
    val session = AVCaptureSession()
    private val metadataOutput = AVCaptureMetadataOutput()
    private var delivered = false
    private var configured = false

    // the delegate is held weakly by AVFoundation
    private val delegate = MetadataDelegate { text ->
        if (!delivered) {
            delivered = true
            onScanned(text)
        }
    }

    suspend fun start() {
        if (configured || !hasCameraAccess()) return
        val camera = AVCaptureDevice.defaultDeviceWithMediaType(MEDIA_TYPE_VIDEO) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, null) as? AVCaptureDeviceInput ?: return

        session.beginConfiguration()
        if (session.canAddInput(input)) session.addInput(input)
        if (session.canAddOutput(metadataOutput)) session.addOutput(metadataOutput)
        // must be set after the output joins the session
        metadataOutput.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
        metadataOutput.metadataObjectTypes = listOf(METADATA_TYPE_QR)
        session.commitConfiguration()
        configured = true

        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            session.startRunning()
        }
    }

    fun stop() {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            session.stopRunning()
        }
    }

    private suspend fun hasCameraAccess(): Boolean =
        when (AVCaptureDevice.authorizationStatusForMediaType(MEDIA_TYPE_VIDEO)) {
            AVAuthorizationStatusAuthorized -> true
            AVAuthorizationStatusNotDetermined -> suspendCancellableCoroutine { continuation ->
                AVCaptureDevice.requestAccessForMediaType(MEDIA_TYPE_VIDEO) { granted -> continuation.resume(granted) }
            }
            else -> false
        }
}

private class MetadataDelegate(
    private val onCode: (String) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        val code = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject ?: return
        code.stringValue?.let(onCode)
    }
}
