package id.tirtapatriot.activitycamera.sync

import id.tirtapatriot.activitycamera.data.CaptureRecord
import id.tirtapatriot.activitycamera.data.CaptureRepository
import id.tirtapatriot.activitycamera.data.LocalFiles
import id.tirtapatriot.activitycamera.data.SettingsStore
import id.tirtapatriot.activitycamera.data.SyncStatus
import id.tirtapatriot.activitycamera.model.ApiContract
import id.tirtapatriot.activitycamera.model.CaptureMetadata
import id.tirtapatriot.activitycamera.model.MediaType
import id.tirtapatriot.activitycamera.model.UploadResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

enum class SyncSkipReason { NOT_SIGNED_IN, ALREADY_RUNNING, SESSION_EXPIRED }

data class SyncResult(val uploaded: Int, val failed: Int, val skipped: SyncSkipReason? = null)

/**
 * Uploads PENDING/FAILED captures with the signed-in officer's token.
 * Files are streamed from disk, so large videos are never loaded into memory.
 */
class SyncClient(
    private val repository: CaptureRepository,
    private val settingsStore: SettingsStore,
    private val api: ServerApi,
) {
    private val running = Mutex()

    suspend fun syncAll(): SyncResult {
        val settings = settingsStore.settings.value
        val token = settings.authToken
        if (!settings.isSignedIn || token == null) return SyncResult(0, 0, SyncSkipReason.NOT_SIGNED_IN)
        if (!running.tryLock()) return SyncResult(0, 0, SyncSkipReason.ALREADY_RUNNING)
        try {
            var uploaded = 0
            var failed = 0
            val queue = repository.items.value.filter { it.status == SyncStatus.PENDING || it.status == SyncStatus.FAILED }
            for (record in queue.reversed()) { // oldest first
                val id = record.meta.clientId
                repository.update(id) { it.copy(status = SyncStatus.SYNCING, lastError = null) }
                try {
                    val response = upload(record, settings.serverUrl, token)
                    repository.update(id) { it.copy(status = SyncStatus.SYNCED, serverId = response.serverId) }
                    uploaded++
                } catch (e: UnauthorizedException) {
                    repository.update(id) { it.copy(status = SyncStatus.PENDING) }
                    settingsStore.signOut()
                    return SyncResult(uploaded, failed, SyncSkipReason.SESSION_EXPIRED)
                } catch (e: Throwable) {
                    repository.update(id) { it.copy(status = SyncStatus.FAILED, lastError = e.message ?: e.toString()) }
                    failed++
                }
            }
            return SyncResult(uploaded, failed)
        } finally {
            running.unlock()
        }
    }

    private suspend fun upload(record: CaptureRecord, serverUrl: String, token: String): UploadResponse {
        val path = repository.filePath(record.fileName)
        val size = LocalFiles.size(path) ?: error("Local file not found: ${record.fileName}")
        val mimeType = when (record.meta.mediaType) {
            MediaType.PHOTO -> "image/jpeg"
            MediaType.VIDEO -> if (record.fileName.endsWith(".mov", true)) "video/quicktime" else "video/mp4"
        }

        val response = api.http.post(serverUrl + ApiContract.CAPTURES) {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // metadata is sent before the file
                        append(
                            ApiContract.PART_METADATA,
                            api.json.encodeToString(CaptureMetadata.serializer(), record.meta),
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
        if (response.status == HttpStatusCode.Unauthorized) throw UnauthorizedException()
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}")
        }
        return response.body()
    }
}
