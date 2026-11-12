package id.tirtapatriot.activitycamera.camera

import id.tirtapatriot.activitycamera.i18n.Strings
import id.tirtapatriot.activitycamera.location.GeoFix
import id.tirtapatriot.activitycamera.model.MediaType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.uuid.Uuid

object StampFormatter {
    private const val MAX_NOTE_LENGTH = 60

    /** Stamp lines: date-time, coordinates, note, officer code. */
    fun lines(
        epochMs: Long,
        timeZone: TimeZone,
        fix: GeoFix?,
        note: String,
        userCode: String,
        strings: Strings,
    ): List<String> = buildList {
        add(dateTime(epochMs, timeZone, strings))
        add(
            if (fix == null) {
                strings.locationSearching
            } else {
                buildString {
                    append(fix.latitude.toFixed(6)).append(", ").append(fix.longitude.toFixed(6))
                    fix.accuracyMeters?.let { append("  (±").append(it.roundToLong()).append(" m)") }
                }
            },
        )
        note.trim().takeIf { it.isNotEmpty() }?.let { add(it.take(MAX_NOTE_LENGTH)) }
        userCode.trim().takeIf { it.isNotEmpty() }?.let { add(strings.officer(it)) }
    }

    fun dateTime(epochMs: Long, timeZone: TimeZone, strings: Strings): String {
        val instant = Instant.fromEpochMilliseconds(epochMs)
        val local = instant.toLocalDateTime(timeZone)
        val offset = timeZone.offsetAt(instant)
        val wholeHours = offset.totalSeconds.takeIf { it % 3600 == 0 }?.div(3600)
        val zone = strings.zoneLabel(wholeHours, "UTC$offset")
        return "${strings.weekdays[local.dayOfWeek.ordinal]}, ${local.dayOfMonth} " +
            "${strings.months[local.monthNumber - 1]} ${local.year} " +
            "${local.hour.pad2()}:${local.minute.pad2()}:${local.second.pad2()} $zone"
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
