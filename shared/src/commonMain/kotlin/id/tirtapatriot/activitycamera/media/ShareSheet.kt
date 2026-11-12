package id.tirtapatriot.activitycamera.media

import id.tirtapatriot.activitycamera.camera.WatermarkFormatter
import id.tirtapatriot.activitycamera.camera.toFixed
import id.tirtapatriot.activitycamera.data.CaptureRecord
import id.tirtapatriot.activitycamera.i18n.Strings
import id.tirtapatriot.activitycamera.model.MediaType
import kotlinx.datetime.TimeZone

/** One file handed to the system share sheet. */
data class ShareFile(val path: String, val mimeType: String)

/**
 * Opens the system share sheet (WhatsApp, Telegram, e-mail, …) with files plus a caption.
 * [onShared] runs once the officer actually picked a target (Android: when the sheet opens).
 */
expect object ShareSheet {
    fun share(files: List<ShareFile>, text: String, onShared: () -> Unit)
}

fun CaptureRecord.mimeType(): String = when (meta.mediaType) {
    MediaType.PHOTO -> "image/jpeg"
    MediaType.VIDEO -> if (fileName.endsWith(".mov", ignoreCase = true)) "video/quicktime" else "video/mp4"
}

/** Caption that goes along with shared captures, like a field report to a supervisor or group. */
object ShareText {
    fun forCapture(record: CaptureRecord, organization: String?, officer: String?, strings: Strings): String {
        val meta = record.meta
        val zone = runCatching { TimeZone.of(meta.timezone) }.getOrDefault(TimeZone.currentSystemDefault())
        return buildList {
            val header = listOfNotNull(organization?.takeIf { it.isNotBlank() }, meta.taskName).joinToString(" — ")
            if (header.isNotEmpty()) add(header)
            add(WatermarkFormatter.dateTime(meta.takenAtEpochMs, zone, strings))
            officer?.let { add(strings.officer(it)) }
            meta.note?.takeIf { it.isNotBlank() }?.let { add(it) }
            mapsLink(meta.latitude, meta.longitude)?.let { add(it) }
        }.joinToString("\n")
    }

    fun forCaptures(records: List<CaptureRecord>, organization: String?, officer: String?, strings: Strings): String {
        if (records.size == 1) return forCapture(records.first(), organization, officer, strings)
        val ordered = records.sortedBy { it.meta.takenAtEpochMs }
        return buildList {
            val tasks = ordered.mapNotNull { it.meta.taskName }.distinct()
            val header = listOfNotNull(organization?.takeIf { it.isNotBlank() }, tasks.singleOrNull()).joinToString(" — ")
            if (header.isNotEmpty()) add(header)
            add(strings.captureCount(ordered.size))
            officer?.let { add(strings.officer(it)) }
            ordered.forEachIndexed { index, record ->
                val meta = record.meta
                val zone = runCatching { TimeZone.of(meta.timezone) }.getOrDefault(TimeZone.currentSystemDefault())
                val line = buildString {
                    append(index + 1).append(". ").append(WatermarkFormatter.dateTime(meta.takenAtEpochMs, zone, strings))
                    if (tasks.size > 1) meta.taskName?.let { append(" · ").append(it) }
                    meta.note?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    mapsLink(meta.latitude, meta.longitude)?.let { append("\n   ").append(it) }
                }
                add(line)
            }
        }.joinToString("\n")
    }

    fun mapsLink(latitude: Double?, longitude: Double?): String? {
        if (latitude == null || longitude == null) return null
        return "https://maps.google.com/?q=${latitude.toFixed(6)},${longitude.toFixed(6)}"
    }
}
