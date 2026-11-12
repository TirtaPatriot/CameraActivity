package id.tirtapatriot.activitycamera

import androidx.compose.ui.window.ComposeUIViewController
import id.tirtapatriot.activitycamera.ui.App

/** Called from Swift: MainViewControllerKt.MainViewController() */
fun MainViewController() = ComposeUIViewController {
    // On iOS the system prompts for camera/microphone/location on first use.
    App(cameraPermissionGranted = true, onRequestPermissions = {})
}
