package id.tirtapatriot.activitycamera.platform

import androidx.compose.runtime.Composable

expect object PlatformInfo {
    /** "android" or "ios" */
    val name: String
    val deviceModel: String
    /** "mp4" on Android (CameraX), "mov" on iOS (AVCaptureMovieFileOutput). */
    val videoExtension: String
}

/** Local app folder holding photos/videos plus index.json. */
expect fun mediaDirectory(): String

/** System back (Android back gesture/button); no-op on iOS. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
