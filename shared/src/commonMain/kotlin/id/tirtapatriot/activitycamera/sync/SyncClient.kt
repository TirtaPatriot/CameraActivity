package id.tirtapatriot.activitycamera.sync

import id.tirtapatriot.activitycamera.data.AppSettings
import id.tirtapatriot.activitycamera.data.CaptureRecord
import id.tirtapatriot.activitycamera.data.CaptureRepository
import id.tirtapatriot.activitycamera.data.LocalFiles
import id.tirtapatriot.activitycamera.data.SettingsStore
import id.tirtapatriot.activitycamera.data.SyncStatus
import id.tirtapatriot.activitycamera.model.ApiContract
import id.tirtapatriot.activitycamera.model.MediaType
import id.tirtapatriot.activitycamera.model.UploadResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class SyncSkipReason { NOT_CONFIGURED, ALREADY_RUNNING }

data class SyncResult(val uploaded: Int, val failed: Int, val skipped: SyncSkipReason? = null)

/**
 * Uploads PENDING/FAILED captures to the Ktor server.
 * Files are streamed from disk, so large videos are never loaded into memory.
 */
class SyncClient(
    private val repo: CaptureRepository,
    private val settingsStore: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val running = Mutex()

    private val http = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
            requestTimeoutMillis = 30 * 60_000L
        }
    }

    suspend fun syncAll(): SyncResult {
        val settings = settingsStore.settings.value
        if (!settings.isSyncConfigured) return SyncResult(0, 0, SyncSkipReason.NOT_CONFIGURED)
        if (!running.tryLock()) return SyncResult(0, 0, SyncSkipReason.ALREADY_RUNNING)
        try {
            var uploaded = 0
            var failed = 0
            val queue = repo.items.value.filter { it.status == SyncStatus.PENDING || it.status == SyncStatus.FAILED }
            for (record in queue.reversed()) { // oldest first
                val id = record.meta.clientId
                repo.update(id) { it.copy(status = SyncStatus.SYNCING, lastError = null) }
                try {
                    val response = upload(record, settings)
                    repo.update(id) { it.copy(status = SyncStatus.SYNCED, serverId = response.serverId) }
                    uploaded++
                } catch (e: Throwable) {
                    repo.update(id) { it.copy(status = SyncStatus.FAILED, lastError = e.message ?: e.toString()) }
                    failed++
                }
            }
            return SyncResult(uploaded, failed)
        } finally {
            running.unlock()
        }
    }

    private suspend fun upload(record: CaptureRecord, settings: AppSettings): UploadResponse {
        val path = repo.filePath(record.fileName)
        val size = LocalFiles.size(path) ?: error("Local file not found: ${record.fileName}")
        val mimeType = when (record.meta.mediaType) {
            MediaType.PHOTO -> "image/jpeg"
            MediaType.VIDEO -> if (record.fileName.endsWith(".mov", true)) "video/quicktime" else "video/mp4"
        }

        val response = http.post(settings.serverUrl.trimEnd('/') + ApiContract.CAPTURES) {
            header(ApiContract.HEADER_API_KEY, settings.apiKey)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // metadata is sent before the file
                        append(
                            ApiContract.PART_METADATA,
                            json.encodeToString(record.meta),
                            Headers.build { append(HttpHeaders.ContentType, "application/json") },
                        )
                        append(
                            ApiContract.PART_FILE,
                            InputProvider(size) { SystemFileSystem.source(Path(path)).buffered() },
                            Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"${record.fileName}\"")
                            },
                        )
                    },
                ),
            )
        }
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}")
        }
        return response.body()
    }
}
