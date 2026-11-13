package id.tirtapatriot.activitycamera.camera

import id.tirtapatriot.activitycamera.i18n.Strings
import id.tirtapatriot.activitycamera.location.GeoFix
import id.tirtapatriot.activitycamera.model.MediaType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.uuid.Uuid

@Serializable
enum class WatermarkStyle {
    /** Rounded card: big time, date, then the detail lines. */
    CARD,

    /** Full-width band at the top or bottom edge. */
    STRIP,

    /** Text only with a soft shadow. */
    MINIMAL,
}

/** How the officer arranged the watermark; kept on the phone. */
@Serializable
data class WatermarkPrefs(
    val style: WatermarkStyle = WatermarkStyle.CARD,
    val showOrganization: Boolean = true,
    val showCoordinates: Boolean = true,
    val showTask: Boolean = true,
    val showOfficer: Boolean = true,
    val showNote: Boolean = true,
    /** Activity note, stays until the officer changes it (or until the next capture, see below). */
    val note: String = "",
    /** Clear [note] once a photo/video has been taken with it. */
    val clearNoteAfterCapture: Boolean = false,
    /** Free extra line, e.g. a work order number. */
    val extraText: String = "",
    /** Position inside the free space of the frame: 0 = left/top, 1 = right/bottom. */
    val anchorX: Float = 0f,
    val anchorY: Float = 1f,
) {
    companion object {
        const val MAX_TEXT = 80
    }
}

/**
 * Everything needed to draw the watermark, already formatted. Rendered by Compose (live preview)
 * and by the platform burners (Android Canvas, iOS UIKit) with the same proportions:
 * all sizes are multiples of [unit] = 2.6 % of the shorter side of the frame.
 */
data class WatermarkSpec(
    val style: WatermarkStyle,
    /** "11:13" */
    val time: String,
    /** "Kamis, 12 Nov 2026 · WIB" */
    val date: String,
    /** Emphasized line (task, or the organization when no task). */
    val title: String?,
    val lines: List<String>,
    /** Accent color (ARGB) taken from the organization's branding. */
    val accentArgb: Long,
    val anchorX: Float,
    val anchorY: Float,
) {
    /** Plain text for EXIF ImageDescription and accessibility. */
    fun plainText(): String = (listOf("$time $date") + listOfNotNull(title) + lines).joinToString(" | ")

    companion object {
        const val UNIT_FRACTION = 0.026f
        const val TIME_SIZE = 2.6f
        const val DATE_SIZE = 0.95f
        const val TITLE_SIZE = 1.1f
        const val LINE_SIZE = 0.95f
        const val PADDING = 0.9f
        const val MARGIN = 1.0f
        const val ACCENT_WIDTH = 0.35f
        const val LINE_SPACING = 1.3f
        const val STRIP_TIME_SIZE = 1.6f
        const val MINIMAL_TIME_SIZE = 1.8f
        const val BACKGROUND_ALPHA_CARD = 0.45f
        const val BACKGROUND_ALPHA_STRIP = 0.55f

        /** Top-left corner of a box of [boxWidth]×[boxHeight] placed by the anchors inside a [width]×[height] frame. */
        fun place(width: Float, height: Float, boxWidth: Float, boxHeight: Float, anchorX: Float, anchorY: Float, margin: Float): Pair<Float, Float> {
            val freeX = (width - 2 * margin - boxWidth).coerceAtLeast(0f)
            val freeY = (height - 2 * margin - boxHeight).coerceAtLeast(0f)
            return (margin + freeX * anchorX.coerceIn(0f, 1f)) to (margin + freeY * anchorY.coerceIn(0f, 1f))
        }
    }
}

/** Builds watermark content and file names from the moment of capture. */
object WatermarkFormatter {
    private const val MAX_LINE = 60

    fun build(
        prefs: WatermarkPrefs,
        epochMs: Long,
        timeZone: TimeZone,
        fix: GeoFix?,
        organization: String?,
        taskName: String?,
        officer: String?,
        accentArgb: Long,
        strings: Strings,
    ): WatermarkSpec {
        val instant = Instant.fromEpochMilliseconds(epochMs)
        val local = instant.toLocalDateTime(timeZone)
        val org = organization?.trim()?.takeIf { prefs.showOrganization && it.isNotEmpty() }?.take(MAX_LINE)
        val task = taskName?.trim()?.takeIf { prefs.showTask && it.isNotEmpty() }?.take(MAX_LINE)
        val title = task ?: org
        val lines = buildList {
            if (task != null && org != null) add(org)
            if (prefs.showCoordinates) add(fix?.let(::coordinates) ?: strings.locationSearching)
            if (prefs.showOfficer) officer?.trim()?.takeIf { it.isNotEmpty() }?.let { add(strings.officer(it).take(MAX_LINE)) }
            if (prefs.showNote) prefs.note.trim().takeIf { it.isNotEmpty() }?.let { add(it.take(MAX_LINE)) }
            prefs.extraText.trim().takeIf { it.isNotEmpty() }?.let { add(it.take(MAX_LINE)) }
        }
        return WatermarkSpec(
            style = prefs.style,
            time = "${local.hour.pad2()}:${local.minute.pad2()}",
            date = "${strings.weekdays[local.dayOfWeek.ordinal]}, ${local.dayOfMonth} ${strings.months[local.monthNumber - 1]} " +
                "${local.year} · ${zone(instant, timeZone, strings)}",
            title = title,
            lines = lines,
            accentArgb = accentArgb,
            anchorX = prefs.anchorX,
            anchorY = prefs.anchorY,
        )
    }

    fun coordinates(fix: GeoFix): String = buildString {
        append(fix.latitude.toFixed(6)).append(", ").append(fix.longitude.toFixed(6))
        fix.accuracyMeters?.let { append("  ±").append(it.roundToLong()).append(" m") }
    }

    /** "Kamis, 12 Nov 2026 11:13 WIB" */
    fun dateTime(epochMs: Long, timeZone: TimeZone, strings: Strings): String {
        val instant = Instant.fromEpochMilliseconds(epochMs)
        val local = instant.toLocalDateTime(timeZone)
        return "${strings.weekdays[local.dayOfWeek.ordinal]}, ${local.dayOfMonth} ${strings.months[local.monthNumber - 1]} " +
            "${local.year} ${local.hour.pad2()}:${local.minute.pad2()} ${zone(instant, timeZone, strings)}"
    }

    private fun zone(instant: Instant, timeZone: TimeZone, strings: Strings): String {
        val offset = timeZone.offsetAt(instant)
        val wholeHours = offset.totalSeconds.takeIf { it % 3600 == 0 }?.div(3600)
        return strings.zoneLabel(wholeHours, "UTC$offset")
    }

    /** File name, e.g. IMG_20261112_111305_a1b2c3.jpg */
    fun fileName(type: MediaType, epochMs: Long, timeZone: TimeZone, clientId: String, videoExtension: String): String {
        val local = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone)
        val prefix = if (type == MediaType.PHOTO) "IMG" else "VID"
        val extension = if (type == MediaType.PHOTO) "jpg" else videoExtension
        return "${prefix}_${local.year}${local.monthNumber.pad2()}${local.dayOfMonth.pad2()}_" +
            "${local.hour.pad2()}${local.minute.pad2()}${local.second.pad2()}_${clientId.take(6)}.$extension"
    }

    fun newClientId(): String = Uuid.random().toString()

    private fun Int.pad2() = toString().padStart(2, '0')
}

/** Fixed-decimal formatting without String.format (not available in common code). */
internal fun Double.toFixed(decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val scaled = (abs(this) * factor).roundToLong()
    val divisor = factor.toLong()
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    return "$sign${scaled / divisor}.${(scaled % divisor).toString().padStart(decimals, '0')}"
}
