package id.tirtapatriot.activitycamera.data

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.random.Random

/** Cross-platform local file access based on kotlinx-io. */
internal object LocalFiles {
    fun ensureDir(path: String) {
        val p = Path(path)
        if (!SystemFileSystem.exists(p)) SystemFileSystem.createDirectories(p)
    }

    fun exists(path: String): Boolean = SystemFileSystem.exists(Path(path))

    fun size(path: String): Long? = SystemFileSystem.metadataOrNull(Path(path))?.size

    fun readText(path: String): String? {
        val p = Path(path)
        if (!SystemFileSystem.exists(p)) return null
        return SystemFileSystem.source(p).buffered().use { it.readString() }
    }

    /** Writes through a unique temp file then moves it, so a file survives the app dying mid-write. */
    fun writeText(path: String, text: String) {
        val tmp = Path("$path.${Random.nextLong().toULong()}.tmp")
        SystemFileSystem.sink(tmp).buffered().use { it.writeString(text) }
        SystemFileSystem.atomicMove(tmp, Path(path))
    }

    fun delete(path: String) {
        SystemFileSystem.delete(Path(path), mustExist = false)
    }
}
