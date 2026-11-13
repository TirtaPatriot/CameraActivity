package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import id.tirtapatriot.activitycamera.camera.TextMeasure
import id.tirtapatriot.activitycamera.camera.WatermarkLayout
import id.tirtapatriot.activitycamera.camera.WatermarkLayoutResult
import id.tirtapatriot.activitycamera.camera.WatermarkOp
import id.tirtapatriot.activitycamera.camera.WatermarkSpec

/**
 * Live watermark over the camera preview, painted from the same layout as the burned photo.
 * Drag it to move; [onMove] receives new anchor fractions, [onMoveEnd] fires when the finger lifts.
 */
@Composable
fun WatermarkOverlay(
    spec: WatermarkSpec,
    modifier: Modifier = Modifier,
    draggable: Boolean = true,
    /** How the phone is held (0/90/180/270): the photo will be landscape at 90/270, so preview it that way. */
    rotation: Int = 0,
    onMove: (anchorX: Float, anchorY: Float) -> Unit = { _, _ -> },
    onMoveEnd: () -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    // Plain holder (not state): written while drawing, read only by the drag handler.
    val lastLayout = remember { LayoutHolder() }
    val currentSpec by rememberUpdatedState(spec)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnMoveEnd by rememberUpdatedState(onMoveEnd)

    val gestures = if (!draggable) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            var dragging = false
            var anchorX = 0f
            var anchorY = 0f
            detectDragGestures(
                onDragStart = { start ->
                    val box = lastLayout.value
                    dragging = box != null &&
                        start.x in (box.left - 24f)..(box.left + box.width + 24f) &&
                        start.y in (box.top - 24f)..(box.top + box.height + 24f)
                    anchorX = currentSpec.anchorX
                    anchorY = currentSpec.anchorY
                },
                onDragEnd = { if (dragging) currentOnMoveEnd(); dragging = false },
                onDragCancel = { if (dragging) currentOnMoveEnd(); dragging = false },
                onDrag = { change, amount ->
                    val box = lastLayout.value
                    if (dragging && box != null) {
                        change.consume()
                        if (box.freeWidth > 0f) anchorX = (anchorX + amount.x / box.freeWidth).coerceIn(0f, 1f)
                        if (box.freeHeight > 0f) anchorY = (anchorY + amount.y / box.freeHeight).coerceIn(0f, 1f)
                        currentOnMove(anchorX, anchorY)
                    }
                },
            )
        }
    }

    Canvas(modifier.then(gestures)) {
        val measure = TextMeasure { text, sizePx, bold ->
            textMeasurer.measure(text, styleFor(this, sizePx, bold, 0xFFFFFFFFL, false), softWrap = false, maxLines = 1).size.width.toFloat()
        }
        if (rotation == 0) {
            val layout = WatermarkLayout.layout(spec, size.width, size.height, measure)
            lastLayout.value = layout
            drawWatermark(layout, textMeasurer)
        } else {
            // Lay out on the frame the photo will have, then turn it to read upright for the officer.
            val sideways = rotation == 90 || rotation == 270
            val frameWidth = if (sideways) size.height else size.width
            val frameHeight = if (sideways) size.width else size.height
            val layout = WatermarkLayout.layout(spec, frameWidth, frameHeight, measure)
            lastLayout.value = null
            rotate(-rotation.toFloat(), pivot = center) {
                translate((size.width - frameWidth) / 2f, (size.height - frameHeight) / 2f) {
                    drawWatermark(layout, textMeasurer)
                }
            }
        }
    }
}

/** Paints laid-out watermark operations; also used for the small style previews. */
fun DrawScope.drawWatermark(layout: WatermarkLayoutResult, textMeasurer: TextMeasurer) {
    layout.ops.forEach { op ->
        when (op) {
            is WatermarkOp.Box -> drawRoundRect(
                color = Color(op.argb and 0xFFFFFFFFL),
                topLeft = Offset(op.left, op.top),
                size = Size(op.right - op.left, op.bottom - op.top),
                cornerRadius = CornerRadius(op.radius, op.radius),
            )

            is WatermarkOp.Text -> {
                val measured = textMeasurer.measure(op.text, styleFor(this, op.sizePx, op.bold, op.argb, op.shadow), softWrap = false, maxLines = 1)
                val top = op.top + (op.lineHeight - measured.size.height) / 2f
                drawText(measured, topLeft = Offset(op.left, top))
            }
        }
    }
}

private fun styleFor(density: Density, sizePx: Float, bold: Boolean, argb: Long, shadow: Boolean) = TextStyle(
    color = Color(argb and 0xFFFFFFFFL),
    fontSize = pxToSp(density, sizePx),
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.6f), Offset(1f, 1f), sizePx * 0.12f) else null,
)

private fun pxToSp(density: Density, px: Float): TextUnit = (px / (density.density * density.fontScale)).sp

private class LayoutHolder {
    var value: WatermarkLayoutResult? = null
}
