package id.tirtapatriot.activitycamera.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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

    override suspend fun takePhoto(outputPath: String, stamp: Stamp): Boolean {
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
        if (!saved) return false
        return withContext(Dispatchers.IO) {
            try {
                Watermark.burn(rawFile, File(outputPath), stamp)
                true
            } catch (_: Throwable) {
                false
            } finally {
                rawFile.delete()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startRecording(outputPath: String, withAudio: Boolean): Boolean {
        if (activeRecording != null) return false
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val audioConfig = if (withAudio && micGranted) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED
        val finished = CompletableDeferred<Boolean>()
        recordingFinished = finished
        activeRecording = cameraX.startRecording(
            FileOutputOptions.Builder(File(outputPath)).build(),
            audioConfig,
            ContextCompat.getMainExecutor(context),
        ) { event ->
            if (event is VideoRecordEvent.Finalize) {
                activeRecording = null
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
}
