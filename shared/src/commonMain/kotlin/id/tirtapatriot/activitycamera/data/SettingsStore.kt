package id.tirtapatriot.activitycamera.data

import id.tirtapatriot.activitycamera.i18n.AppLanguage
import id.tirtapatriot.activitycamera.platform.mediaDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AppSettings(
    /** Sync server base URL, e.g. http://192.168.1.10:8080 */
    val serverUrl: String = "",
    val apiKey: String = "",
    /** Officer code / employee ID, also printed on the photo stamp. */
    val userCode: String = "",
    val recordAudio: Boolean = true,
    /** UI and stamp language. Indonesian by default. */
    val language: AppLanguage = AppLanguage.INDONESIAN,
) {
    val isSyncConfigured: Boolean get() = serverUrl.isNotBlank() && apiKey.isNotBlank()
}

class SettingsStore(baseDir: String = mediaDirectory()) {
    private val json = Json { ignoreUnknownKeys = true }
    private val path = "$baseDir/settings.json"

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        LocalFiles.ensureDir(baseDir)
    }

    private fun load(): AppSettings =
        LocalFiles.readText(path)
            ?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() }
            ?: AppSettings()

    fun save(value: AppSettings) {
        LocalFiles.writeText(path, json.encodeToString(value))
        _settings.value = value
    }
}
