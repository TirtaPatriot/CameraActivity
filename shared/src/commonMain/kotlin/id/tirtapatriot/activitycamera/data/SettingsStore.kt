package id.tirtapatriot.activitycamera.data

import id.tirtapatriot.activitycamera.i18n.AppLanguage
import id.tirtapatriot.activitycamera.model.AppConfig
import id.tirtapatriot.activitycamera.model.UserProfile
import id.tirtapatriot.activitycamera.platform.mediaDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppSettings(
    /** Base URL of the organization's server, e.g. https://camera.example.org */
    val serverUrl: String = "",
    /** Last configuration (branding + capture policy) fetched from the server; used offline. */
    val serverConfig: AppConfig? = null,
    /** Bearer token from sign-in; null when signed out. */
    val authToken: String? = null,
    val user: UserProfile? = null,
    /** UI language picked on this phone; null = the server's default language. */
    val language: AppLanguage? = null,
) {
    val isConnected: Boolean get() = serverUrl.isNotBlank()
    val isSignedIn: Boolean get() = isConnected && authToken != null && user != null
    val config: AppConfig get() = serverConfig ?: AppConfig()
    val effectiveLanguage: AppLanguage get() = language ?: AppLanguage.fromCode(config.capture.defaultLanguage)
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
        _settings = MutableStateFlow(load())
        settings = _settings.asStateFlow()
    }

    private fun load(): AppSettings =
        LocalFiles.readText(path)
            ?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings()

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(_settings.value)
        LocalFiles.writeText(path, json.encodeToString(AppSettings.serializer(), next))
        _settings.value = next
    }

    fun connect(serverUrl: String, config: AppConfig) = update {
        AppSettings(serverUrl = serverUrl, serverConfig = config, language = it.language)
    }

    fun signedIn(token: String, user: UserProfile) = update { it.copy(authToken = token, user = user) }

    fun signOut() = update { it.copy(authToken = null, user = null) }

    /** Forget the server entirely (captures already on the phone are kept). */
    fun disconnect() = update { AppSettings(language = it.language) }
}
