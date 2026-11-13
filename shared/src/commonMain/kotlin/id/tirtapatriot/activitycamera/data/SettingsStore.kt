package id.tirtapatriot.activitycamera.data

import id.tirtapatriot.activitycamera.camera.WatermarkPrefs
import id.tirtapatriot.activitycamera.i18n.AppLanguage
import id.tirtapatriot.activitycamera.model.AppConfig
import id.tirtapatriot.activitycamera.model.TaskType
import id.tirtapatriot.activitycamera.model.UserProfile
import id.tirtapatriot.activitycamera.platform.PlatformInfo
import id.tirtapatriot.activitycamera.platform.mediaDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

@Serializable
data class AppSettings(
    /** Device id sent with every capture: the OS id shared with the other in-house apps, else a random id. */
    val deviceId: String = "",
    /** Base URL of the organization's server, e.g. https://camera.example.org; empty = not connected. */
    val serverUrl: String = "",
    /** Last configuration (branding, rules, tasks) fetched from the server; used offline. */
    val serverConfig: AppConfig? = null,
    /** Bearer token from sign-in; null when signed out (anonymous use). */
    val authToken: String? = null,
    val user: UserProfile? = null,
    /** Set when the server ended the session; cleared on the next sign-in. */
    val sessionExpired: Boolean = false,
    /** UI language picked on this phone; null = the server's default language. */
    val language: AppLanguage? = null,
    /** Task from the server picked in the camera. */
    val selectedTaskId: Long? = null,
    /** Tasks the officer typed in on this phone. */
    val customTasks: List<String> = emptyList(),
    /** One of [customTasks] picked instead of a server task. */
    val selectedCustomTask: String? = null,
    /** After the shutter, show the capture first; it is only kept and uploaded once confirmed. */
    val confirmAfterCapture: Boolean = false,
    /** Save front-camera photos mirrored, as seen on screen; off = true to life, text in the scene readable. */
    val mirrorFrontPhotos: Boolean = false,
    /** Watermark as arranged by the officer. */
    val watermark: WatermarkPrefs = WatermarkPrefs(),
) {
    val isConnected: Boolean get() = serverUrl.isNotBlank()
    val isSignedIn: Boolean get() = isConnected && authToken != null && user != null
    val config: AppConfig get() = serverConfig ?: AppConfig()
    val effectiveLanguage: AppLanguage get() = language ?: AppLanguage.fromCode(config.capture.defaultLanguage)

    /** The picked task, if the server still offers it. */
    val selectedTask: TaskType? get() = selectedTaskId?.let { id -> config.tasks.firstOrNull { it.id == id } }

    /** Name of the picked task: a server task or one typed in by the officer. */
    val pickedTaskName: String? get() = selectedTask?.name ?: selectedCustomTask?.takeIf { it in customTasks }

    /** Uploads are possible: connected, and signed in unless the server accepts anonymous phones. */
    val canUpload: Boolean
        get() = isConnected && (isSignedIn || (config.capture.allowAnonymousUpload && !config.capture.requireSignIn))

    /** "Name (code)" of the signed-in officer, or null. */
    val officerLabel: String?
        get() = user?.let { account -> account.userCode?.let { "${account.displayName} ($it)" } ?: account.displayName }
}

/** App settings and session, stored as settings.json in the app's private folder. */
class SettingsStore(baseDir: String = mediaDirectory()) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val path = "$baseDir/settings.json"

    private val _settings: MutableStateFlow<AppSettings>
    val settings: StateFlow<AppSettings>

    init {
        LocalFiles.ensureDir(baseDir)
        val loaded = load()
        _settings = MutableStateFlow(loaded)
        settings = _settings.asStateFlow()
        // Prefer the OS id so it matches the attendance app; fall back to a random id kept per install.
        val systemId = runCatching { PlatformInfo.systemDeviceId() }.getOrNull()
        when {
            systemId != null && loaded.deviceId != systemId -> update { it.copy(deviceId = systemId) }
            loaded.deviceId.isBlank() -> update { it.copy(deviceId = Uuid.random().toString()) }
        }
    }

    private fun load(): AppSettings =
        LocalFiles.readText(path)
            ?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings()

    fun update(change: (AppSettings) -> AppSettings) {
        val next = _settings.updateAndGet(change)
        runCatching { LocalFiles.writeText(path, json.encodeToString(AppSettings.serializer(), next)) }
    }

    fun connect(serverUrl: String, config: AppConfig) = update {
        it.copy(serverUrl = serverUrl, serverConfig = config, authToken = null, user = null, sessionExpired = false, selectedTaskId = null)
    }

    fun signedIn(token: String, user: UserProfile) = update { it.copy(authToken = token, user = user, sessionExpired = false) }

    fun signOut(expired: Boolean = false) = update { it.copy(authToken = null, user = null, sessionExpired = expired) }

    /** Forget the server entirely (captures already on the phone are kept). */
    fun disconnect() = update {
        it.copy(serverUrl = "", serverConfig = null, authToken = null, user = null, sessionExpired = false, selectedTaskId = null)
    }
}
