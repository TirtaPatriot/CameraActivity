package id.tirtapatriot.activitycamera.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import id.tirtapatriot.activitycamera.location.GeoFix
import java.io.File
import java.io.FileOutputStream

/** Burns the watermark into a JPEG (same layout as the live preview) and writes GPS into EXIF. */
internal object Watermark {
    private const val JPEG_QUALITY = 90

    fun burn(source: File, destination: File, spec: WatermarkSpec?, geo: GeoFix?) {
        val rotationDegrees = when (
            ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        val decoded = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inMutable = true })
            ?: error("Unable to decode photo")
        val upright = if (rotationDegrees != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotationDegrees) }, true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val bitmap = if (upright.isMutable) upright else upright.copy(Bitmap.Config.ARGB_8888, true).also { upright.recycle() }

        if (spec != null) draw(Canvas(bitmap), bitmap.width.toFloat(), bitmap.height.toFloat(), spec)

        FileOutputStream(destination).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()

        ExifInterface(destination.absolutePath).apply {
            geo?.let {
                setLatLong(it.latitude, it.longitude)
                it.altitude?.let { altitude -> setAltitude(altitude) }
            }
            spec?.let { setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, it.plainText()) }
            saveAttributes()
        }
    }

    private fun draw(canvas: Canvas, width: Float, height: Float, spec: WatermarkSpec) {
        val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        val measure = TextMeasure { text, sizePx, isBold ->
            val paint = if (isBold) bold else regular
            paint.textSize = sizePx
            paint.measureText(text)
        }
        val layout = WatermarkLayout.layout(spec, width, height, measure)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        layout.ops.forEach { op ->
            when (op) {
                is WatermarkOp.Box -> {
                    fill.color = op.argb.toInt()
                    canvas.drawRoundRect(RectF(op.left, op.top, op.right, op.bottom), op.radius, op.radius, fill)
                }

                is WatermarkOp.Text -> {
                    val paint = Paint(if (op.bold) bold else regular).apply {
                        textSize = op.sizePx
                        color = op.argb.toInt()
                        if (op.shadow) setShadowLayer(op.sizePx * 0.12f, 1f, 1f, 0x99000000.toInt()) else clearShadowLayer()
                    }
                    val metrics = paint.fontMetrics
                    // Center the font's ascent..descent inside the line box.
                    val baseline = op.top + (op.lineHeight - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
                    canvas.drawText(op.text, op.left, baseline, paint)
                }
            }
        }
    }
}
