package id.tirtapatriot.activitycamera.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import id.tirtapatriot.activitycamera.location.GeoFix

enum class FlashMode { OFF, ON, AUTO }

/**
 * A photo straight from the sensor, not yet watermarked. Capturing is fast; the slow part
 * (decode, draw, JPEG encode) happens in [saveWithWatermark] off the main thread, so the
 * shutter is ready again immediately.
 */
interface RawPhoto {
    /** Draws [spec] (null = no watermark), writes GPS into EXIF and saves a JPEG to [outputPath]. */
    suspend fun saveWithWatermark(outputPath: String, spec: WatermarkSpec?, geo: GeoFix?): Boolean

    /** Drops the raw data without saving. */
    fun discard()
}

/** Cross-platform camera control. Implemented with CameraX (Android) and AVFoundation (iOS). */
interface CameraController {
    val isRecording: Boolean

    /** Captures a photo as fast as possible; null on failure. */
    suspend fun capture(flash: FlashMode): RawPhoto?

    /** Starts recording video to [outputPath]. */
    fun startRecording(outputPath: String, withAudio: Boolean, flash: FlashMode): Boolean

    /** Stops recording; true when the video file was saved. */
    suspend fun stopRecording(): Boolean

    /** Switches between the back and front camera. */
    fun switchCamera()

    /** Zoom ratio, e.g. 1f or 2f; clamped to what the camera supports. */
    fun setZoom(ratio: Float)
}

/** Native camera preview; [onReady] is called once the camera can be used. */
@Composable
expect fun CameraPreview(modifier: Modifier, onReady: (CameraController) -> Unit)
