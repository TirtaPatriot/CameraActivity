package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.tirtapatriot.activitycamera.AppGraph
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.i18n.stringsFor
import id.tirtapatriot.activitycamera.location.rememberLocationProvider
import id.tirtapatriot.activitycamera.platform.PlatformBackHandler
import kotlinx.coroutines.launch

private sealed interface Route {
    data object Camera : Route
    data class Review(val clientId: String) : Route
    data object History : Route
    data object Account : Route
    data object Connect : Route
    data object Login : Route
}

/**
 * Root UI shared by Android and iOS. The camera is always the first screen; a server and an
 * account are optional and only suggested. Branding, rules and tasks come from the server
 * (cached for offline use).
 * @param cameraPermissionGranted always true on iOS (the system prompts when the camera is opened).
 */
@Composable
fun App(cameraPermissionGranted: Boolean, onRequestPermissions: () -> Unit) {
    val store = AppGraph.settingsStore
    val api = AppGraph.api
    val settings by store.settings.collectAsState()
    var stack by remember { mutableStateOf(listOf<Route>(Route.Camera)) }
    val route = stack.last()

    fun push(next: Route) {
        stack = stack + next
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    fun popToCamera() {
        stack = listOf(Route.Camera)
    }

    // Fresh rules & tasks, and anything left to upload, every time the app starts or the server changes.
    LaunchedEffect(settings.serverUrl) {
        AppGraph.refreshConfig()
        AppGraph.requestSync()
    }
    // A sign-in or a server allowing anonymous phones may unblock waiting uploads.
    LaunchedEffect(settings.canUpload, settings.authToken) {
        if (settings.canUpload) AppGraph.requestSync()
    }

    PlatformBackHandler(enabled = stack.size > 1) { pop() }

    val strings = stringsFor(settings.effectiveLanguage)
    CompositionLocalProvider(LocalStrings provides strings) {
        BrandedTheme(settings.config.branding.primaryColor) {
            // Screens stack in one Box (the live camera can sit under the review screen).
            Box(Modifier.fillMaxSize()) {
            // The camera stays alive under the review screen, so going back to it is instant
            // (no camera restart). Same slot in the tree for both routes keeps its state.
            val cameraAlive = cameraPermissionGranted && (route == Route.Camera || (route is Route.Review && stack.size == 2))
            if (cameraAlive) {
                CameraScreen(
                    settings = settings,
                    locationProvider = rememberLocationProvider(),
                    onOpenReview = { push(Route.Review(it)) },
                    onOpenHistory = { push(Route.History) },
                    onOpenAccount = { push(Route.Account) },
                )
            }
            when (val current = route) {
                Route.Camera -> if (!cameraPermissionGranted) PermissionPrompt(onRequestPermissions)

                is Route.Review -> ReviewScreen(
                    settings = settings,
                    clientId = current.clientId,
                    onBack = { pop() },
                    onRetake = { popToCamera() },
                )

                Route.History -> HistoryScreen(
                    settings = settings,
                    onBack = { pop() },
                    onOpen = { push(Route.Review(it)) },
                    onOpenAccount = { push(Route.Account) },
                )

                Route.Account -> AccountScreen(
                    settings = settings,
                    onBack = { pop() },
                    onSignIn = { push(Route.Login) },
                    onConnect = { push(Route.Connect) },
                    onSignOut = {
                        val token = settings.authToken
                        val serverUrl = settings.serverUrl
                        store.signOut()
                        if (token != null) AppGraph.appScope.launch { api.logout(serverUrl, token) }
                    },
                    onDisconnect = { store.disconnect() },
                )

                Route.Connect -> ConnectScreen(
                    api = api,
                    cameraPermissionGranted = cameraPermissionGranted,
                    onRequestPermissions = onRequestPermissions,
                    onConnected = { url, config ->
                        store.connect(url, config)
                        // Next gentle step: offer sign-in, which can be skipped.
                        stack = stack.dropLast(1) + Route.Login
                    },
                    onLater = { pop() },
                )

                Route.Login -> LoginScreen(
                    settings = settings,
                    api = api,
                    notice = strings.sessionExpired.takeIf { settings.sessionExpired },
                    onSignedIn = { token, user ->
                        store.signedIn(token, user)
                        pop()
                    },
                    onChangeServer = { stack = stack.dropLast(1) + Route.Connect },
                    onSkip = { pop() },
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
        Modifier.fillMaxSize().background(Color(0xFF0E1116)).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Pin, null, tint = Color.White, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(16.dp))
        Text(strings.permissionRationale, textAlign = TextAlign.Center, color = Color.White)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest, shape = RoundedCornerShape(14.dp)) { Text(strings.grantPermission) }
    }
}
