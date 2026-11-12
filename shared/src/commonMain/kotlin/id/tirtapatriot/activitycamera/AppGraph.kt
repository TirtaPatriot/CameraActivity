package id.tirtapatriot.activitycamera

import id.tirtapatriot.activitycamera.data.CaptureRepository
import id.tirtapatriot.activitycamera.data.SettingsStore
import id.tirtapatriot.activitycamera.sync.BackgroundSync
import id.tirtapatriot.activitycamera.sync.ServerApi
import id.tirtapatriot.activitycamera.sync.SyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** App-wide singletons, shared by the UI and the background sync worker. */
object AppGraph {
    /** Work that must finish even if the screen that started it goes away (saving photos, sync). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsStore: SettingsStore by lazy { SettingsStore() }
    val repository: CaptureRepository by lazy { CaptureRepository() }
    val api: ServerApi by lazy { ServerApi() }
    val syncClient: SyncClient by lazy { SyncClient(repository, settingsStore, api) }

    fun requestSync() = BackgroundSync.request()

    /** Fetches the latest branding, rules and tasks; keeps the cached copy when offline. */
    fun refreshConfig() {
        val serverUrl = settingsStore.settings.value.serverUrl
        if (serverUrl.isBlank()) return
        appScope.launch {
            runCatching { api.fetchConfig(serverUrl) }.onSuccess { latest ->
                settingsStore.update { if (it.serverUrl == serverUrl && it.serverConfig != latest) it.copy(serverConfig = latest) else it }
            }
        }
    }
}
