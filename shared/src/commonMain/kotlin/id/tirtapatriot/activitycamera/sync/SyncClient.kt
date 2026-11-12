package id.tirtapatriot.activitycamera.sync

import id.tirtapatriot.activitycamera.data.CaptureRecord
import id.tirtapatriot.activitycamera.data.CaptureRepository
import id.tirtapatriot.activitycamera.data.LocalFiles
import id.tirtapatriot.activitycamera.data.SettingsStore
import id.tirtapatriot.activitycamera.data.SyncStatus
import id.tirtapatriot.activitycamera.media.mimeType
import id.tirtapatriot.activitycamera.model.ApiContract
import id.tirtapatriot.activitycamera.model.CaptureMetadata
import id.tirtapatriot.activitycamera.model.ShareReport
import id.tirtapatriot.activitycamera.model.UploadResponse
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

enum class SyncSkipReason { NOT_CONNECTED, SIGN_IN_REQUIRED, ALREADY_RUNNING, SESSION_EXPIRED }

data class SyncResult(val uploaded: Int, val failed: Int, val skipped: SyncSkipReason? = null) {
    val shouldRetry: Boolean get() = failed > 0 || skipped == SyncSkipReason.ALREADY_RUNNING
}

/**
 * Uploads captures that are not on the server yet — with the officer's token when signed in,
 * or anonymously (per device) when the server allows it — then reports late shares.
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
        if (!settings.isConnected) return SyncResult(0, 0, SyncSkipReason.NOT_CONNECTED)
        if (!settings.canUpload) return SyncResult(0, 0, SyncSkipReason.SIGN_IN_REQUIRED)
        val token = settings.authToken.takeIf { settings.isSignedIn }
        if (!running.tryLock()) return SyncResult(0, 0, SyncSkipReason.ALREADY_RUNNING)
        try {
            var uploaded = 0
            var failed = 0
            // SYNCING left over from a killed process is retried too.
            val queue = repository.items.value.filter { it.status != SyncStatus.SYNCED }
            for (record in queue.reversed()) { // oldest first
                val id = record.clientId
                repository.update(id) { it.copy(status = SyncStatus.SYNCING, lastError = null) }
                try {
                    val latest = repository.find(id) ?: continue
                    val metadata = latest.meta.copy(
                        deviceId = latest.meta.deviceId ?: settings.deviceId,
                        sharedAtEpochMs = latest.sharedAtEpochMs,
                    )
                    val response = upload(latest, metadata, settings.serverUrl, token)
                    repository.update(id) {
                        it.copy(
                            status = SyncStatus.SYNCED,
                            serverId = response.serverId,
                            sharedReported = it.sharedAtEpochMs != null && it.sharedAtEpochMs == metadata.sharedAtEpochMs,
                        )
                    }
                    uploaded++
                } catch (_: UnauthorizedException) {
                    repository.update(id) { it.copy(status = SyncStatus.PENDING) }
                    return if (token != null) {
                        settingsStore.signOut(expired = true)
                        SyncResult(uploaded, failed, SyncSkipReason.SESSION_EXPIRED)
                    } else {
                        SyncResult(uploaded, failed, SyncSkipReason.SIGN_IN_REQUIRED)
                    }
                } catch (e: Throwable) {
                    repository.update(id) { it.copy(status = SyncStatus.FAILED, lastError = e.message ?: e.toString()) }
                    failed++
                }
            }
            failed += reportShares(settings.serverUrl, token)
            return SyncResult(uploaded, failed)
        } finally {
            running.unlock()
        }
    }

    /** Captures shared after they were uploaded; returns how many reports failed. */
    private suspend fun reportShares(serverUrl: String, token: String?): Int {
        var failed = 0
        val unreported = repository.items.value.filter {
            it.status == SyncStatus.SYNCED && it.sharedAtEpochMs != null && !it.sharedReported
        }
        for (record in unreported) {
            val sharedAt = record.sharedAtEpochMs ?: continue
            val ok = runCatching {
                val response = api.http.post(serverUrl + ApiContract.sharedPath(record.clientId)) {
                    authorize(token)
                    contentType(ContentType.Application.Json)
                    setBody(ShareReport(sharedAt))
                }
                // 404: the capture is gone on the server; nothing left to report.
                response.status.isSuccess() || response.status == HttpStatusCode.NotFound
            }.getOrDefault(false)
            if (ok) repository.update(record.clientId) { it.copy(sharedReported = true) } else failed++
        }
        return failed
    }

    private suspend fun upload(record: CaptureRecord, metadata: CaptureMetadata, serverUrl: String, token: String?): UploadResponse {
        val path = repository.filePath(record)
        val size = LocalFiles.size(path) ?: error("Local file not found: ${record.fileName}")
        val response = api.http.post(serverUrl + ApiContract.CAPTURES) {
            authorize(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // metadata is sent before the file
                        append(
                            ApiContract.PART_METADATA,
                            api.json.encodeToString(CaptureMetadata.serializer(), metadata),
                            Headers.build { append(HttpHeaders.ContentType, "application/json") },
                        )
                        append(
                            ApiContract.PART_FILE,
                            InputProvider(size) { SystemFileSystem.source(Path(path)).buffered() },
                            Headers.build {
                                append(HttpHeaders.ContentType, record.mimeType())
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

    private fun HttpRequestBuilder.authorize(token: String?) {
        if (token != null) bearerAuth(token)
    }
}
