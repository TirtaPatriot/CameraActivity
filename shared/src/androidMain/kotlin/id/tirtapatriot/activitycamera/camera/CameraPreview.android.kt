package id.tirtapatriot.activitycamera.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.tirtapatriot.activitycamera.location.GeoFix
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import androidx.camera.view.CameraController as CameraXController

@Composable
actual fun CameraPreview(modifier: Modifier, onReady: (CameraController) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraXController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraXController.IMAGE_CAPTURE or CameraXController.VIDEO_CAPTURE)
            // Shutter first: the watermark is drawn afterwards, off the main thread.
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    DisposableEffect(lifecycleOwner) {
        cameraXController.bindToLifecycle(lifecycleOwner)
        onDispose { cameraXController.unbind() }
    }

    AndroidView(
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // SurfaceView path: lowest latency and power use.
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                controller = cameraXController
            }
        },
        modifier = modifier,
    )

    LaunchedEffect(cameraXController) {
        onReady(AndroidCameraController(context.applicationContext, cameraXController))
    }
}

private class AndroidCameraController(
    private val context: Context,
    private val cameraX: LifecycleCameraController,
) : CameraController {
    private var activeRecording: Recording? = null
    private var recordingFinished: CompletableDeferred<Boolean>? = null

    override val isRecording: Boolean get() = activeRecording != null

    override suspend fun capture(flash: FlashMode): RawPhoto? {
        cameraX.imageCaptureFlashMode = when (flash) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
        val rawFile = File(context.cacheDir, "capture_${System.nanoTime()}.jpg")
        val saved = suspendCancellableCoroutine<Boolean> { continuation ->
            cameraX.takePicture(
                ImageCapture.OutputFileOptions.Builder(rawFile).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
        }
        if (!saved) {
            rawFile.delete()
            return null
        }
        return AndroidRawPhoto(rawFile)
    }

    @SuppressLint("MissingPermission")
    override fun startRecording(outputPath: String, withAudio: Boolean, flash: FlashMode): Boolean {
        if (activeRecording != null) return false
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val audioConfig = if (withAudio && micGranted) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED
        if (flash == FlashMode.ON) cameraX.enableTorch(true)
        val finished = CompletableDeferred<Boolean>()
        recordingFinished = finished
        activeRecording = cameraX.startRecording(
            FileOutputOptions.Builder(File(outputPath)).build(),
            audioConfig,
            ContextCompat.getMainExecutor(context),
        ) { event ->
            if (event is VideoRecordEvent.Finalize) {
                activeRecording = null
                cameraX.enableTorch(false)
                finished.complete(!event.hasError())
            }
        }
        return true
    }

    override suspend fun stopRecording(): Boolean {
        val recording = activeRecording ?: return false
        recording.stop()
        return recordingFinished?.await() ?: false
    }

    override fun switchCamera() {
        cameraX.cameraSelector =
            if (cameraX.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }

    override fun setZoom(ratio: Float) {
        val state = cameraX.zoomState.value
        val clamped = if (state != null) ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio) else ratio
        cameraX.setZoomRatio(clamped)
    }
}

private class AndroidRawPhoto(private val file: File) : RawPhoto {
    override suspend fun saveWithWatermark(outputPath: String, spec: WatermarkSpec?, geo: GeoFix?): Boolean =
        withContext(Dispatchers.Default) {
            try {
                Watermark.burn(file, File(outputPath), spec, geo)
                true
            } catch (_: Throwable) {
                false
            } finally {
                file.delete()
            }
        }

    override fun discard() {
        file.delete()
    }
}
