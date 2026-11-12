package id.tirtapatriot.activitycamera.server

import id.tirtapatriot.activitycamera.model.CaptureMetadata
import id.tirtapatriot.activitycamera.model.MediaType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId

class PayloadTooLargeException(limitBytes: Long) :
    RuntimeException("File exceeds the ${limitBytes / 1024 / 1024} MB limit")

/**
 * File storage on the NAS (mounted folder).
 * Layout: <root>/photo|video/yyyy/MM/dd/<clientId>.<ext>
 */
class MediaStorage(root: String) {
    private val rootDir: File = File(root).canonicalFile
    private val tempDir = File(rootDir, ".tmp")

    /** Fail fast at startup when the NAS is not mounted or not writable. */
    fun checkWritable() {
        tempDir.mkdirs()
        require(tempDir.isDirectory && tempDir.canWrite()) { "STORAGE_ROOT is not writable: $rootDir" }
    }

    /** Streams an upload into a temp file on the NAS (never held in memory). */
    suspend fun saveTemp(channel: ByteReadChannel, maxBytes: Long): File = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("upload_", ".part", tempDir)
        try {
            channel.toInputStream().use { input ->
                tempFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw PayloadTooLargeException(maxBytes)
                        output.write(buffer, 0, read)
                    }
                }
            }
            tempFile
        } catch (e: Throwable) {
            tempFile.delete()
            throw e
        }
    }

    /** Moves the temp file to its final location; returns the path relative to the root. */
    fun commit(tempFile: File, metadata: CaptureMetadata, originalName: String?): String {
        val zone = runCatching { ZoneId.of(metadata.timezone) }.getOrDefault(ZoneId.systemDefault())
        val date = Instant.ofEpochMilli(metadata.takenAtEpochMs).atZone(zone).toLocalDate()
        val folder = if (metadata.mediaType == MediaType.PHOTO) "photo" else "video"
        val extension = originalName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
            ?: if (metadata.mediaType == MediaType.PHOTO) "jpg" else "mp4"

        val relativePath = "%s/%04d/%02d/%02d/%s.%s".format(
            folder, date.year, date.monthValue, date.dayOfMonth, metadata.clientId, extension,
        )
        val destination = File(rootDir, relativePath)
        destination.parentFile.mkdirs()
        Files.move(tempFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return relativePath
    }

    /** Resolves a relative path from the DB, rejecting anything outside the root. */
    fun resolve(relativePath: String): File? {
        val file = File(rootDir, relativePath).canonicalFile
        return file.takeIf { it.path.startsWith(rootDir.path + File.separator) && it.isFile }
    }
}
