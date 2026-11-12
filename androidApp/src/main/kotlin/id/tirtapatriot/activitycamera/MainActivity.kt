package id.tirtapatriot.activitycamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import id.tirtapatriot.activitycamera.platform.AndroidPlatform
import id.tirtapatriot.activitycamera.ui.App

class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private var isCameraGranted by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            isCameraGranted = isGranted(Manifest.permission.CAMERA)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidPlatform.init(applicationContext)
        enableEdgeToEdge()

        isCameraGranted = isGranted(Manifest.permission.CAMERA)
        if (requiredPermissions.any { !isGranted(it) }) permissionLauncher.launch(requiredPermissions)

        setContent {
            App(
                cameraPermissionGranted = isCameraGranted,
                onRequestPermissions = { permissionLauncher.launch(requiredPermissions) },
            )
        }
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
