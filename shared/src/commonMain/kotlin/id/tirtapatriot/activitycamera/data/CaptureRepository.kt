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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
)

/**
 * Captures stored on the device, persisted as index.json in the media folder.
 * Good enough to start with; can be replaced by SQLDelight/Room KMP as data grows.
 */
class CaptureRepository(private val baseDir: String = mediaDirectory()) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val indexPath = "$baseDir/index.json"

    private val _items = MutableStateFlow<List<CaptureRecord>>(emptyList())
    val items: StateFlow<List<CaptureRecord>> = _items.asStateFlow()

    init {
        LocalFiles.ensureDir(baseDir)
        _items.value = load()
    }

    fun filePath(fileName: String): String = "$baseDir/$fileName"

    private fun load(): List<CaptureRecord> =
        LocalFiles.readText(indexPath)
            ?.let { runCatching { json.decodeFromString<List<CaptureRecord>>(it) }.getOrNull() }
            .orEmpty()

    suspend fun add(record: CaptureRecord) = mutate { listOf(record) + it }

    suspend fun update(clientId: String, change: (CaptureRecord) -> CaptureRecord) = mutate { list ->
        list.map { if (it.meta.clientId == clientId) change(it) else it }
    }

    private suspend fun mutate(change: (List<CaptureRecord>) -> List<CaptureRecord>) =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val next = change(_items.value)
                LocalFiles.writeText(indexPath, json.encodeToString(next))
                _items.value = next
            }
        }
}
