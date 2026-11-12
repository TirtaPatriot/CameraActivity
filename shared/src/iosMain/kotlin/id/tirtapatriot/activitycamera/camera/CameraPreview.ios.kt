@file:OptIn(ExperimentalForeignApi::class)

package id.tirtapatriot.activitycamera.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import id.tirtapatriot.activitycamera.location.GeoFix
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
// Star import: several AVCaptureDevice properties live in ObjC categories (Kotlin extensions).
import platform.AVFoundation.*
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
    private var videoInput: AVCaptureDeviceInput? = null
    private var isConfigured = false
    private var isRecordingNow = false

    // AVFoundation delegates are weak/transient, so keep references here
    private var photoDelegate: PhotoCaptureDelegate? = null
    private var recordingDelegate: RecordingDelegate? = null
    private var recordingFinished: CompletableDeferred<Boolean>? = null

    override val isRecording: Boolean get() = isRecordingNow

    private val currentDevice: AVCaptureDevice? get() = videoInput?.device

    /** Requests camera (& microphone) access, configures the session, then starts it. */
    suspend fun prepare(): Boolean {
        if (isConfigured) return true
        if (!requestAccess(MEDIA_TYPE_VIDEO)) return false
        val microphoneAllowed = requestAccess(MEDIA_TYPE_AUDIO)

        session.beginConfiguration()
        // 4:3 photos and preview, matching the viewfinder and the watermark layout
        session.sessionPreset = AVCaptureSessionPresetPhoto ?: "AVCaptureSessionPresetPhoto"

        val cameraInput = cameraDevice(AVCaptureDevicePositionBack)
            ?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) as? AVCaptureDeviceInput }
        if (cameraInput == null || !session.canAddInput(cameraInput)) {
            session.commitConfiguration()
            return false
        }
        session.addInput(cameraInput)
        videoInput = cameraInput

        if (microphoneAllowed) {
            AVCaptureDevice.defaultDeviceWithMediaType(MEDIA_TYPE_AUDIO)
                ?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) as? AVCaptureDeviceInput }
                ?.let { if (session.canAddInput(it)) session.addInput(it) }
        }
        if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput)
        if (session.canAddOutput(movieOutput)) session.addOutput(movieOutput)
        lockPortrait()

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

    override suspend fun capture(flash: FlashMode): RawPhoto? {
        if (!isConfigured) return null
        val settings = AVCapturePhotoSettings.photoSettings()
        if (currentDevice?.hasFlash == true) {
            settings.flashMode = when (flash) {
                FlashMode.OFF -> AVCaptureFlashModeOff
                FlashMode.ON -> AVCaptureFlashModeOn
                FlashMode.AUTO -> AVCaptureFlashModeAuto
            }
        }
        val data = suspendCancellableCoroutine<NSData?> { continuation ->
            val delegate = PhotoCaptureDelegate { result -> if (continuation.isActive) continuation.resume(result) }
            photoDelegate = delegate
            photoOutput.capturePhotoWithSettings(settings, delegate)
        } ?: return null
        return IosRawPhoto(data)
    }

    /** On iOS the microphone input is attached in prepare() when allowed; [withAudio] is not used yet. */
    override fun startRecording(outputPath: String, withAudio: Boolean, flash: FlashMode): Boolean {
        if (!isConfigured || isRecordingNow) return false
        if (flash == FlashMode.ON) setTorch(true)
        val finished = CompletableDeferred<Boolean>()
        recordingFinished = finished
        val delegate = RecordingDelegate { success ->
            isRecordingNow = false
            setTorch(false)
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

    override fun switchCamera() {
        if (!isConfigured || isRecordingNow) return
        val current = videoInput ?: return
        val nextPosition = if (current.device.position == AVCaptureDevicePositionBack) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack
        val nextInput = cameraDevice(nextPosition)
            ?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) as? AVCaptureDeviceInput } ?: return
        session.beginConfiguration()
        session.removeInput(current)
        if (session.canAddInput(nextInput)) {
            session.addInput(nextInput)
            videoInput = nextInput
        } else {
            session.addInput(current)
        }
        lockPortrait()
        session.commitConfiguration()
    }

    override fun setZoom(ratio: Float) {
        val device = currentDevice ?: return
        if (!device.lockForConfiguration(null)) return
        device.videoZoomFactor = ratio.toDouble().coerceIn(1.0, device.activeFormat.videoMaxZoomFactor)
        device.unlockForConfiguration()
    }

    private fun setTorch(on: Boolean) {
        val device = currentDevice ?: return
        if (!device.hasTorch || !device.lockForConfiguration(null)) return
        device.torchMode = if (on) AVCaptureTorchModeOn else AVCaptureTorchModeOff
        device.unlockForConfiguration()
    }

    /** The app is locked to portrait. */
    private fun lockPortrait() {
        photoOutput.connectionWithMediaType(MEDIA_TYPE_VIDEO)?.videoOrientation = AVCaptureVideoOrientationPortrait
        movieOutput.connectionWithMediaType(MEDIA_TYPE_VIDEO)?.videoOrientation = AVCaptureVideoOrientationPortrait
    }

    private fun cameraDevice(position: Long): AVCaptureDevice? =
        AVCaptureDevice.defaultDeviceWithDeviceType(
            AVCaptureDeviceTypeBuiltInWideAngleCamera ?: "AVCaptureDeviceTypeBuiltInWideAngleCamera",
            MEDIA_TYPE_VIDEO,
            position,
        )

    private suspend fun requestAccess(mediaType: String): Boolean =
        when (AVCaptureDevice.authorizationStatusForMediaType(mediaType)) {
            AVAuthorizationStatusAuthorized -> true
            AVAuthorizationStatusNotDetermined -> suspendCancellableCoroutine { continuation ->
                AVCaptureDevice.requestAccessForMediaType(mediaType) { granted -> continuation.resume(granted) }
            }
            else -> false
        }
}

private class IosRawPhoto(private var data: NSData?) : RawPhoto {
    override suspend fun saveWithWatermark(outputPath: String, spec: WatermarkSpec?, geo: GeoFix?): Boolean {
        val bytes = data ?: return false
        data = null
        return withContext(Dispatchers.Default) { IosWatermark.burn(bytes, spec, outputPath) }
    }

    override fun discard() {
        data = null
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
