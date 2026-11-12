package id.tirtapatriot.activitycamera.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/** Burns the stamp text into a JPEG and writes GPS into EXIF. */
internal object Watermark {
    private const val JPEG_QUALITY = 92

    fun burn(source: File, destination: File, stamp: Stamp) {
        val rotationDegrees = when (
            ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        val decoded = BitmapFactory.decodeFile(source.absolutePath) ?: error("Unable to decode photo")
        val upright = if (rotationDegrees != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotationDegrees) }, true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val canvasBitmap = if (upright.isMutable) upright else upright.copy(Bitmap.Config.ARGB_8888, true).also { upright.recycle() }

        drawStamp(Canvas(canvasBitmap), canvasBitmap.width.toFloat(), canvasBitmap.height.toFloat(), stamp.lines)

        FileOutputStream(destination).use { canvasBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        canvasBitmap.recycle()

        ExifInterface(destination.absolutePath).apply {
            stamp.geo?.let { geo ->
                setLatLong(geo.latitude, geo.longitude)
                geo.altitude?.let { setAltitude(it) }
            }
            setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, stamp.lines.joinToString(" | "))
            saveAttributes()
        }
    }

    private fun drawStamp(canvas: Canvas, width: Float, height: Float, lines: List<String>) {
        val textSize = min(width, height) * 0.032f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(textSize * 0.12f, 1f, 1f, Color.BLACK)
        }
        val padding = textSize * 0.6f
        val lineHeight = textSize * 1.3f
        val boxWidth = lines.maxOf { textPaint.measureText(it) } + padding * 2
        val boxHeight = lineHeight * lines.size + padding * 2
        val left = padding
        val top = height - boxHeight - padding

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(115, 0, 0, 0) }
        canvas.drawRoundRect(RectF(left, top, left + boxWidth, top + boxHeight), padding, padding, backgroundPaint)
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, left + padding, top + padding + textSize + index * lineHeight, textPaint)
        }
    }
}
