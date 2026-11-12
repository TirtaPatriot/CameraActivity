package id.tirtapatriot.activitycamera.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Decodes a downscaled image (or a video frame) whose longer side is at most [maxSize] px; null when it can't. */
expect suspend fun decodeThumbnail(path: String, isVideo: Boolean, maxSize: Int): ImageBitmap?

/** Small in-memory cache so scrolling the history never decodes the same file twice. */
object ThumbnailCache {
    private const val MAX_ENTRIES = 160
    private val entries = LinkedHashMap<String, ImageBitmap>()
    // Limits parallel decodes so a fast scroll doesn't spike memory.
    private val decoding = Semaphore(3)

    // Only touched from the main thread (composition / produceState).
    fun get(key: String): ImageBitmap? = entries[key]

    fun put(key: String, bitmap: ImageBitmap) {
        entries.remove(key)
        entries[key] = bitmap
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }

    suspend fun load(path: String, isVideo: Boolean, maxSize: Int): ImageBitmap? {
        val key = "$path@$maxSize"
        get(key)?.let { return it }
        val bitmap = decoding.withPermit { withContext(Dispatchers.Default) { decodeThumbnail(path, isVideo, maxSize) } }
        bitmap?.let { put(key, it) }
        return bitmap
    }
}

@Composable
fun rememberThumbnail(path: String, isVideo: Boolean, maxSize: Int): State<ImageBitmap?> =
    produceState(ThumbnailCache.get("$path@$maxSize"), path, maxSize) {
        if (value == null) value = ThumbnailCache.load(path, isVideo, maxSize)
    }
