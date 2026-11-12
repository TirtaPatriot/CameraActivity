package id.tirtapatriot.activitycamera.camera

import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.camera.view.CameraController as CameraXController

@Composable
actual fun QrScannerView(modifier: Modifier, onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)

    val cameraXController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraXController.IMAGE_ANALYSIS)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val reader = QRCodeReader()
        val delivered = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraXController.setImageAnalysisAnalyzer(executor) { image ->
            try {
                if (!delivered.get()) {
                    decodeQr(image, reader)?.let { text ->
                        if (delivered.compareAndSet(false, true)) mainExecutor.execute { currentOnScanned(text) }
                    }
                }
            } finally {
                image.close()
            }
        }
        cameraXController.bindToLifecycle(lifecycleOwner)

        onDispose {
            cameraXController.clearImageAnalysisAnalyzer()
            cameraXController.unbind()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = cameraXController
            }
        },
        modifier = modifier,
    )
}

/** Decodes a QR code from the luminance (Y) plane of a YUV_420_888 frame. */
private fun decodeQr(image: ImageProxy, reader: QRCodeReader): String? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    val source = PlanarYUVLuminanceSource(
        data, plane.rowStride, image.height, 0, 0, image.width, image.height, false,
    )
    return try {
        reader.decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}
