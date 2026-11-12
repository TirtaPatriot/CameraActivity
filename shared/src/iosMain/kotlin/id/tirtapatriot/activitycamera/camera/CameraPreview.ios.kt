@file:OptIn(ExperimentalForeignApi::class)

package id.tirtapatriot.activitycamera.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureFileOutput
import platform.AVFoundation.AVCaptureFileOutputRecordingDelegateProtocol
import platform.AVFoundation.AVCaptureMovieFileOutput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import kotlin.coroutines.resume

// AVFoundation constants are bridged as String?; fall back to their actual values.
internal val MEDIA_TYPE_VIDEO: String = AVMediaTypeVideo ?: "vide"
private val MEDIA_TYPE_AUDIO: String = AVMediaTypeAudio ?: "soun"

@Composable
actual fun CameraPreview(modifier: Modifier, onReady: (CameraController) -> Unit) {
    val controller = remember { IosCameraController() }

    LaunchedEffect(controller) {
        if (controller.prepare()) onReady(controller)
    }
    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }

    UIKitView(
        factory = { PreviewView(controller.session) },
        modifier = modifier,
    )
}

/** UIView hosting an AVCaptureVideoPreviewLayer that follows the view's size. */
internal class PreviewView(session: AVCaptureSession) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill ?: "AVLayerVideoGravityResizeAspectFill"
    }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
    }
}

private class IosCameraController : CameraController {
    val session = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private val movieOutput = AVCaptureMovieFileOutput()
    private var isConfigured = false
    private var isRecordingNow = false

    // AVFoundation delegates are weak/transient, so keep references here
    private var photoDelegate: PhotoCaptureDelegate? = null
    private var recordingDelegate: RecordingDelegate? = null
    private var recordingFinished: CompletableDeferred<Boolean>? = null

    override val isRecording: Boolean get() = isRecordingNow

    /** Requests camera (& microphone) access, configures the session, then starts it. */
    suspend fun prepare(): Boolean {
        if (isConfigured) return true
        if (!requestAccess(MEDIA_TYPE_VIDEO)) return false
        val microphoneAllowed = requestAccess(MEDIA_TYPE_AUDIO)

        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetHigh ?: "AVCaptureSessionPresetHigh"

        val camera = AVCaptureDevice.defaultDeviceWithMediaType(MEDIA_TYPE_VIDEO)
        val cameraInput = camera?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) as? AVCaptureDeviceInput }
        if (cameraInput == null || !session.canAddInput(cameraInput)) {
            session.commitConfiguration()
            return false
        }
        session.addInput(cameraInput)

        if (microphoneAllowed) {
            AVCaptureDevice.defaultDeviceWithMediaType(MEDIA_TYPE_AUDIO)
                ?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) as? AVCaptureDeviceInput }
                ?.let { if (session.canAddInput(it)) session.addInput(it) }
        }
        if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput)
        if (session.canAddOutput(movieOutput)) session.addOutput(movieOutput)

        // the app is locked to portrait
        photoOutput.connectionWithMediaType(MEDIA_TYPE_VIDEO)?.videoOrientation = AVCaptureVideoOrientationPortrait
        movieOutput.connectionWithMediaType(MEDIA_TYPE_VIDEO)?.videoOrientation = AVCaptureVideoOrientationPortrait

        session.commitConfiguration()
        isConfigured = true
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            session.startRunning()
        }
        return true
    }

    fun shutdown() {
        if (isRecordingNow) movieOutput.stopRecording()
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            session.stopRunning()
        }
    }

    override suspend fun takePhoto(outputPath: String, stamp: Stamp): Boolean {
        if (!isConfigured) return false
        val data = suspendCancellableCoroutine<NSData?> { continuation ->
            val delegate = PhotoCaptureDelegate { result -> if (continuation.isActive) continuation.resume(result) }
            photoDelegate = delegate
            photoOutput.capturePhotoWithSettings(AVCapturePhotoSettings.photoSettings(), delegate)
        } ?: return false
        return withContext(Dispatchers.Default) { IosWatermark.burn(data, stamp, outputPath) }
    }

    /** On iOS the microphone input is attached in prepare() when allowed; [withAudio] is not used yet. */
    override fun startRecording(outputPath: String, withAudio: Boolean): Boolean {
        if (!isConfigured || isRecordingNow) return false
        val finished = CompletableDeferred<Boolean>()
        recordingFinished = finished
        val delegate = RecordingDelegate { success ->
            isRecordingNow = false
            finished.complete(success)
        }
        recordingDelegate = delegate
        movieOutput.startRecordingToOutputFileURL(NSURL.fileURLWithPath(outputPath), recordingDelegate = delegate)
        isRecordingNow = true
        return true
    }

    override suspend fun stopRecording(): Boolean {
        if (!isRecordingNow) return false
        movieOutput.stopRecording()
        return recordingFinished?.await() ?: false
    }

    private suspend fun requestAccess(mediaType: String): Boolean =
        when (AVCaptureDevice.authorizationStatusForMediaType(mediaType)) {
            AVAuthorizationStatusAuthorized -> true
            AVAuthorizationStatusNotDetermined -> suspendCancellableCoroutine { continuation ->
                AVCaptureDevice.requestAccessForMediaType(mediaType) { granted -> continuation.resume(granted) }
            }
            else -> false
        }
}

private class PhotoCaptureDelegate(
    private val onResult: (NSData?) -> Unit,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
    override fun captureOutput(output: AVCapturePhotoOutput, didFinishProcessingPhoto: AVCapturePhoto, error: NSError?) {
        onResult(if (error == null) didFinishProcessingPhoto.fileDataRepresentation() else null)
    }
}

private class RecordingDelegate(
    private val onFinished: (Boolean) -> Unit,
) : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureFileOutput,
        didFinishRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
        error: NSError?,
    ) {
        onFinished(error == null)
    }
}
