package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.tirtapatriot.activitycamera.data.CaptureRepository
import id.tirtapatriot.activitycamera.data.SettingsStore
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.i18n.stringsFor
import id.tirtapatriot.activitycamera.location.rememberLocationProvider
import id.tirtapatriot.activitycamera.sync.SyncClient

private enum class Screen { CAMERA, GALLERY, SETTINGS }

/**
 * Root UI shared by Android and iOS.
 * @param cameraPermissionGranted always true on iOS (the system prompts when the camera is opened).
 */
@Composable
fun App(cameraPermissionGranted: Boolean, onRequestPermissions: () -> Unit) {
    val repository = remember { CaptureRepository() }
    val settingsStore = remember { SettingsStore() }
    val syncClient = remember { SyncClient(repository, settingsStore) }
    val settings by settingsStore.settings.collectAsState()
    var screen by remember { mutableStateOf(Screen.CAMERA) }

    CompositionLocalProvider(LocalStrings provides stringsFor(settings.language)) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.CAMERA ->
                        if (cameraPermissionGranted) {
                            val locationProvider = rememberLocationProvider()
                            CameraScreen(
                                repository = repository,
                                settings = settings,
                                locationProvider = locationProvider,
                                onOpenGallery = { screen = Screen.GALLERY },
                                onOpenSettings = { screen = Screen.SETTINGS },
                            )
                        } else {
                            PermissionPrompt(onRequestPermissions)
                        }

                    Screen.GALLERY -> GalleryScreen(
                        repository = repository,
                        syncClient = syncClient,
                        isSyncConfigured = settings.isSyncConfigured,
                        onBack = { screen = Screen.CAMERA },
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        initial = settings,
                        onSave = { settingsStore.save(it); screen = Screen.CAMERA },
                        onBack = { screen = Screen.CAMERA },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(strings.permissionRationale, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text(strings.grantPermission) }
    }
}
