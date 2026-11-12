package id.tirtapatriot.activitycamera.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import id.tirtapatriot.activitycamera.location.GeoFix

/** Stamp text (time, coordinates, note) burned into the photo. */
data class Stamp(
    val lines: List<String>,
    val geo: GeoFix?,
)

/** Cross-platform camera control. Implemented with CameraX (Android) and AVFoundation (iOS). */
interface CameraController {
    val isRecording: Boolean

    /** Takes a photo, burns the stamp into it, and saves a JPEG to [outputPath]. */
    suspend fun takePhoto(outputPath: String, stamp: Stamp): Boolean

    /** Starts recording video to [outputPath]. */
    fun startRecording(outputPath: String, withAudio: Boolean): Boolean

    /** Stops recording; true when the video file was saved. */
    suspend fun stopRecording(): Boolean
}

/** Native camera preview; [onReady] is called once the camera can be used. */
@Composable
expect fun CameraPreview(modifier: Modifier, onReady: (CameraController) -> Unit)
