package id.tirtapatriot.activitycamera.data

import id.tirtapatriot.activitycamera.model.CaptureMetadata
import id.tirtapatriot.activitycamera.platform.mediaDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class SyncStatus { PENDING, SYNCING, SYNCED, FAILED }

@Serializable
data class CaptureRecord(
    val meta: CaptureMetadata,
    /** File name inside the local media folder. */
    val fileName: String,
    val status: SyncStatus = SyncStatus.PENDING,
    val serverId: Long? = null,
    val lastError: String? = null,
    /** First time the officer opened the share sheet for this capture. */
    val sharedAtEpochMs: Long? = null,
    /** The server already knows about [sharedAtEpochMs]. */
    val sharedReported: Boolean = false,
    /** The note changed after upload and the server doesn't have it yet. */
    val noteDirty: Boolean = false,
    /** False while waiting for the officer to keep or retake it ("confirm after capture"); not uploaded yet. */
    val confirmed: Boolean = true,
) {
    val clientId: String get() = meta.clientId
}

/**
 * Captures stored on the device, persisted as index.json in the media folder.
 * Good enough to start with; can be replaced by SQLDelight/Room KMP as data grows.
 */
class CaptureRepository(private val baseDir: String = mediaDirectory()) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val indexPath = "$baseDir/index.json"
    private val listSerializer = ListSerializer(CaptureRecord.serializer())

    private val _items = MutableStateFlow<List<CaptureRecord>>(emptyList())
    /** Newest first. */
    val items: StateFlow<List<CaptureRecord>> = _items.asStateFlow()

    init {
        LocalFiles.ensureDir(baseDir)
        // A capture left unconfirmed (app closed during review) is kept rather than lost.
        _items.value = load().map { if (it.confirmed) it else it.copy(confirmed = true) }
    }

    fun filePath(fileName: String): String = "$baseDir/$fileName"

    fun filePath(record: CaptureRecord): String = filePath(record.fileName)

    fun find(clientId: String): CaptureRecord? = _items.value.firstOrNull { it.clientId == clientId }

    private fun load(): List<CaptureRecord> =
        LocalFiles.readText(indexPath)
            ?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }
            .orEmpty()

    suspend fun add(record: CaptureRecord) = mutate { listOf(record) + it }

    suspend fun update(clientId: String, change: (CaptureRecord) -> CaptureRecord) = mutate { list ->
        list.map { if (it.clientId == clientId) change(it) else it }
    }

    suspend fun confirm(clientId: String) = update(clientId) { it.copy(confirmed = true) }

    /** Removes a capture that was never confirmed (retake), including its file. */
    suspend fun discardUnconfirmed(clientId: String) = mutate { list ->
        val record = list.firstOrNull { it.clientId == clientId && !it.confirmed } ?: return@mutate list
        LocalFiles.delete(filePath(record))
        list - record
    }

    /** Changes the note of one capture (sync sends it to the server when already uploaded). */
    suspend fun updateNote(clientId: String, note: String?) = update(clientId) {
        val cleaned = note?.trim()?.ifEmpty { null }
        if (it.meta.note == cleaned) it else it.copy(meta = it.meta.copy(note = cleaned), noteDirty = true)
    }

    /** Marks captures as shared now (only the first share counts). */
    suspend fun markShared(clientIds: Collection<String>, atEpochMs: Long) = mutate { list ->
        list.map { if (it.clientId in clientIds && it.sharedAtEpochMs == null) it.copy(sharedAtEpochMs = atEpochMs) else it }
    }

    private suspend fun mutate(change: (List<CaptureRecord>) -> List<CaptureRecord>) =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val next = change(_items.value)
                LocalFiles.writeText(indexPath, json.encodeToString(listSerializer, next))
                _items.value = next
            }
        }
}
