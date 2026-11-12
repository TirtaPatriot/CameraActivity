package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.tirtapatriot.activitycamera.data.CaptureRepository
import id.tirtapatriot.activitycamera.data.SettingsStore
import id.tirtapatriot.activitycamera.data.SyncStatus
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.i18n.stringsFor
import id.tirtapatriot.activitycamera.location.rememberLocationProvider
import id.tirtapatriot.activitycamera.sync.ServerApi
import id.tirtapatriot.activitycamera.sync.SyncClient
import kotlinx.coroutines.launch

private enum class Screen { CAMERA, GALLERY, SETTINGS }

/**
 * Root UI shared by Android and iOS: connect to a server → sign in → camera.
 * Branding, colors and capture rules come from the server (cached for offline use).
 * @param cameraPermissionGranted always true on iOS (the system prompts when the camera is opened).
 */
@Composable
fun App(cameraPermissionGranted: Boolean, onRequestPermissions: () -> Unit) {
    val repository = remember { CaptureRepository() }
    val settingsStore = remember { SettingsStore() }
    val api = remember { ServerApi() }
    val syncClient = remember { SyncClient(repository, settingsStore, api) }
    val settings by settingsStore.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.CAMERA) }
    var loginNotice by remember { mutableStateOf<String?>(null) }

    // Refresh branding & rules whenever the app starts or the server changes; keep the cached copy offline.
    LaunchedEffect(settings.serverUrl) {
        if (!settings.isConnected) return@LaunchedEffect
        runCatching { api.fetchConfig(settings.serverUrl) }.onSuccess { latest ->
            if (latest != settingsStore.settings.value.serverConfig) settingsStore.update { it.copy(serverConfig = latest) }
        }
    }
    LaunchedEffect(settings.isSignedIn) {
        if (!settings.isSignedIn) screen = Screen.CAMERA
    }

    val strings = stringsFor(settings.effectiveLanguage)

    CompositionLocalProvider(LocalStrings provides strings) {
        BrandedTheme(settings.config.branding.primaryColor) {
            Surface(Modifier.fillMaxSize()) {
                when {
                    !settings.isConnected -> ConnectScreen(
                        api = api,
                        cameraPermissionGranted = cameraPermissionGranted,
                        onRequestPermissions = onRequestPermissions,
                        onConnected = { url, config -> settingsStore.connect(url, config) },
                    )

                    !settings.isSignedIn -> LoginScreen(
                        settings = settings,
                        api = api,
                        notice = loginNotice,
                        onSignedIn = { token, user ->
                            loginNotice = null
                            settingsStore.signedIn(token, user)
                        },
                        onChangeServer = { settingsStore.disconnect() },
                    )

                    else -> when (screen) {
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
                            onSessionExpired = { loginNotice = strings.sessionExpired },
                            onBack = { screen = Screen.CAMERA },
                        )

                        Screen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            api = api,
                            pendingCount = repository.items.value.count { it.status != SyncStatus.SYNCED },
                            onLanguageChange = { language -> settingsStore.update { it.copy(language = language) } },
                            onSignOut = {
                                val token = settings.authToken
                                val serverUrl = settings.serverUrl
                                settingsStore.signOut()
                                if (token != null) scope.launch { api.logout(serverUrl, token) }
                            },
                            onBack = { screen = Screen.CAMERA },
                        )
                    }
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
