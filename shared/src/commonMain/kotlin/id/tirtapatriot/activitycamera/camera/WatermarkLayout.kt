package id.tirtapatriot.activitycamera.camera

import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.ACCENT_WIDTH
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.BACKGROUND_ALPHA_CARD
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.BACKGROUND_ALPHA_STRIP
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.DATE_SIZE
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.LINE_SIZE
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.LINE_SPACING
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.MARGIN
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.MINIMAL_TIME_SIZE
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.PADDING
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.STRIP_TIME_SIZE
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.TIME_SIZE
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.TITLE_SIZE
import id.tirtapatriot.activitycamera.camera.WatermarkSpec.Companion.UNIT_FRACTION
import kotlin.math.max
import kotlin.math.min

/** A primitive to paint; coordinates in pixels of the target frame. */
sealed interface WatermarkOp {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float, val radius: Float, val argb: Long) : WatermarkOp

    /** Text vertically centered inside the line box [top, top + lineHeight]. */
    data class Text(
        val text: String,
        val left: Float,
        val top: Float,
        val lineHeight: Float,
        val sizePx: Float,
        val bold: Boolean,
        val argb: Long,
        val shadow: Boolean,
    ) : WatermarkOp
}

data class WatermarkLayoutResult(
    val ops: List<WatermarkOp>,
    /** Bounds of the whole watermark (for dragging). */
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    /** Free space used to turn a drag distance into anchor fractions. */
    val freeWidth: Float,
    val freeHeight: Float,
)

/** Measures the width of [text] in pixels at [sizePx], bold or regular. */
fun interface TextMeasure {
    fun width(text: String, sizePx: Float, bold: Boolean): Float
}

/**
 * Lays out a [WatermarkSpec] for a frame of [frameWidth]×[frameHeight] px.
 * The same function drives the live preview and the burned photo, so they always match.
 */
object WatermarkLayout {
    private const val WHITE = 0xFFFFFFFFL
    private const val DATE_COLOR = 0xD9FFFFFFL
    private const val LINE_COLOR = 0xF2FFFFFFL
    private const val MAX_WIDTH_FRACTION = 0.86f

    private class Row(val time: String?, val date: String?, val text: String?, val size: Float, val bold: Boolean, val color: Long, val height: Float)

    fun layout(spec: WatermarkSpec, frameWidth: Float, frameHeight: Float, measure: TextMeasure): WatermarkLayoutResult {
        val unit = min(frameWidth, frameHeight) * UNIT_FRACTION
        return when (spec.style) {
            WatermarkStyle.STRIP -> strip(spec, frameWidth, frameHeight, unit, measure)
            WatermarkStyle.CARD, WatermarkStyle.MINIMAL -> block(spec, frameWidth, frameHeight, unit, measure)
        }
    }

    private fun block(spec: WatermarkSpec, frameWidth: Float, frameHeight: Float, u: Float, measure: TextMeasure): WatermarkLayoutResult {
        val isCard = spec.style == WatermarkStyle.CARD
        val shadow = !isCard
        val timeSize = (if (isCard) TIME_SIZE else MINIMAL_TIME_SIZE) * u
        val dateSize = DATE_SIZE * u
        val insetLeft = if (isCard) 1.5f * u else 0f
        val insetRight = if (isCard) PADDING * u else 0f
        val insetVertical = if (isCard) PADDING * u else 0f
        val maxText = frameWidth * MAX_WIDTH_FRACTION - insetLeft - insetRight
        val gap = 0.6f * u

        val timeWidth = measure.width(spec.time, timeSize, true)
        val date = fit(spec.date, dateSize, false, maxText - timeWidth - gap, measure)
        val dateWidth = measure.width(date, dateSize, false)
        val timeRowHeight = timeSize * 1.15f

        val rows = buildList {
            spec.title?.let { add(Row(null, null, fit(it, TITLE_SIZE * u, true, maxText, measure), TITLE_SIZE * u, true, WHITE, TITLE_SIZE * u * LINE_SPACING)) }
            spec.lines.forEach { add(Row(null, null, fit(it, LINE_SIZE * u, false, maxText, measure), LINE_SIZE * u, false, LINE_COLOR, LINE_SIZE * u * LINE_SPACING)) }
        }
        val contentWidth = max(timeWidth + gap + dateWidth, rows.maxOfOrNull { measure.width(it.text.orEmpty(), it.size, it.bold) } ?: 0f)
        val contentHeight = timeRowHeight + rows.sumOf { it.height.toDouble() }.toFloat()
        val boxWidth = contentWidth + insetLeft + insetRight
        val boxHeight = contentHeight + insetVertical * 2
        val margin = MARGIN * u
        val (left, top) = WatermarkSpec.place(frameWidth, frameHeight, boxWidth, boxHeight, spec.anchorX, spec.anchorY, margin)

        val ops = mutableListOf<WatermarkOp>()
        if (isCard) {
            ops += WatermarkOp.Box(left, top, left + boxWidth, top + boxHeight, PADDING * u, alpha(BACKGROUND_ALPHA_CARD))
            val accentLeft = left + 0.45f * u
            ops += WatermarkOp.Box(
                accentLeft, top + insetVertical, accentLeft + ACCENT_WIDTH * u, top + boxHeight - insetVertical,
                ACCENT_WIDTH * u / 2, spec.accentArgb,
            )
        }
        val textLeft = left + insetLeft
        var y = top + insetVertical
        ops += WatermarkOp.Text(spec.time, textLeft, y, timeRowHeight, timeSize, true, WHITE, shadow)
        val dateLineHeight = dateSize * LINE_SPACING
        ops += WatermarkOp.Text(date, textLeft + timeWidth + gap, y + timeRowHeight - dateLineHeight - 0.15f * u, dateLineHeight, dateSize, false, DATE_COLOR, shadow)
        y += timeRowHeight
        rows.forEach { row ->
            ops += WatermarkOp.Text(row.text.orEmpty(), textLeft, y, row.height, row.size, row.bold, row.color, shadow)
            y += row.height
        }
        return WatermarkLayoutResult(
            ops, left, top, boxWidth, boxHeight,
            freeWidth = (frameWidth - 2 * margin - boxWidth).coerceAtLeast(0f),
            freeHeight = (frameHeight - 2 * margin - boxHeight).coerceAtLeast(0f),
        )
    }

    private fun strip(spec: WatermarkSpec, frameWidth: Float, frameHeight: Float, u: Float, measure: TextMeasure): WatermarkLayoutResult {
        val timeSize = STRIP_TIME_SIZE * u
        val dateSize = DATE_SIZE * u
        val lineSize = LINE_SIZE * u
        val insetLeft = 1.5f * u
        val insetRight = 1.2f * u
        val insetVertical = 0.7f * u
        val gap = 0.6f * u
        val maxText = frameWidth - insetLeft - insetRight

        val timeWidth = measure.width(spec.time, timeSize, true)
        val date = fit(spec.date, dateSize, false, maxText - timeWidth - gap, measure)
        val detail = (listOfNotNull(spec.title) + spec.lines).joinToString("  ·  ").takeIf { it.isNotEmpty() }
            ?.let { fit(it, lineSize, false, maxText, measure) }
        val timeRowHeight = timeSize * 1.2f
        val detailHeight = if (detail != null) lineSize * LINE_SPACING else 0f
        val boxHeight = timeRowHeight + detailHeight + insetVertical * 2
        val top = if (spec.anchorY < 0.5f) 0f else frameHeight - boxHeight

        val ops = mutableListOf<WatermarkOp>()
        ops += WatermarkOp.Box(0f, top, frameWidth, top + boxHeight, 0f, alpha(BACKGROUND_ALPHA_STRIP))
        val accentLeft = 0.6f * u
        ops += WatermarkOp.Box(accentLeft, top + insetVertical, accentLeft + ACCENT_WIDTH * u, top + boxHeight - insetVertical, ACCENT_WIDTH * u / 2, spec.accentArgb)
        var y = top + insetVertical
        ops += WatermarkOp.Text(spec.time, insetLeft, y, timeRowHeight, timeSize, true, WHITE, false)
        val dateLineHeight = dateSize * LINE_SPACING
        ops += WatermarkOp.Text(date, insetLeft + timeWidth + gap, y + timeRowHeight - dateLineHeight - 0.1f * u, dateLineHeight, dateSize, false, DATE_COLOR, false)
        y += timeRowHeight
        detail?.let { ops += WatermarkOp.Text(it, insetLeft, y, detailHeight, lineSize, false, LINE_COLOR, false) }
        return WatermarkLayoutResult(ops, 0f, top, frameWidth, boxHeight, freeWidth = 0f, freeHeight = (frameHeight - boxHeight).coerceAtLeast(0f))
    }

    /** Shortens [text] with an ellipsis until it fits [maxWidth]. */
    private fun fit(text: String, sizePx: Float, bold: Boolean, maxWidth: Float, measure: TextMeasure): String {
        if (maxWidth <= 0f) return ""
        if (measure.width(text, sizePx, bold) <= maxWidth) return text
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (measure.width(text.take(mid).trimEnd() + "…", sizePx, bold) <= maxWidth) low = mid else high = mid - 1
        }
        return text.take(low).trimEnd() + "…"
    }

    private fun alpha(fraction: Float): Long = ((fraction * 255).toLong().coerceIn(0, 255) shl 24)
}
