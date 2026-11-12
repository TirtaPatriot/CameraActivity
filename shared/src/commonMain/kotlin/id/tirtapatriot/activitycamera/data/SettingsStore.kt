package id.tirtapatriot.activitycamera.data

import id.tirtapatriot.activitycamera.camera.WatermarkPrefs
import id.tirtapatriot.activitycamera.i18n.AppLanguage
import id.tirtapatriot.activitycamera.model.AppConfig
import id.tirtapatriot.activitycamera.model.TaskType
import id.tirtapatriot.activitycamera.model.UserProfile
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
    /** Random id made once per install; lets the server group anonymous uploads per phone. */
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
    /** Task picked in the camera (optional). */
    val selectedTaskId: Long? = null,
    /** Watermark as arranged by the officer. */
    val watermark: WatermarkPrefs = WatermarkPrefs(),
) {
    val isConnected: Boolean get() = serverUrl.isNotBlank()
    val isSignedIn: Boolean get() = isConnected && authToken != null && user != null
    val config: AppConfig get() = serverConfig ?: AppConfig()
    val effectiveLanguage: AppLanguage get() = language ?: AppLanguage.fromCode(config.capture.defaultLanguage)

    /** The picked task, if the server still offers it. */
    val selectedTask: TaskType? get() = selectedTaskId?.let { id -> config.tasks.firstOrNull { it.id == id } }

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
        if (loaded.deviceId.isBlank()) update { it.copy(deviceId = Uuid.random().toString()) }
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
