package id.tirtapatriot.activitycamera.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual suspend fun decodeThumbnail(path: String, isVideo: Boolean, maxSize: Int): ImageBitmap? = runCatching {
    val bitmap = if (isVideo) videoFrame(path, maxSize) else imageThumbnail(path, maxSize)
    bitmap?.asImageBitmap()
}.getOrNull()

private fun imageThumbnail(path: String, maxSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    return scaleDown(decoded, maxSize)
}

private fun videoFrame(path: String, maxSize: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.getFrameAtTime(0)?.let { scaleDown(it, maxSize) }
    } finally {
        retriever.release()
    }
}

private fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= maxSize) return bitmap
    val scale = maxSize.toFloat() / longest
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        .also { if (it !== bitmap) bitmap.recycle() }
}
