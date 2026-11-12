@file:OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)

package id.tirtapatriot.activitycamera.media

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min

actual suspend fun decodeThumbnail(path: String, isVideo: Boolean, maxSize: Int): ImageBitmap? {
    val image = (if (isVideo) videoFrame(path) else UIImage.imageWithContentsOfFile(path)) ?: return null
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = min(1.0, maxSize / max(width, height))
    val targetWidth = width * scale
    val targetHeight = height * scale
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    val small = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth, targetHeight), format = format).imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    }
    val data = UIImageJPEGRepresentation(small, 0.8) ?: return null
    val length = data.length.toInt()
    if (length == 0) return null
    val bytes = ByteArray(length).apply { usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    return runCatching { bytes.decodeToImageBitmap() }.getOrNull()
}

private fun videoFrame(path: String): UIImage? {
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
    val generator = AVAssetImageGenerator(asset = asset).apply { appliesPreferredTrackTransform = true }
    val cgImage = generator.copyCGImageAtTime(CMTimeMake(0, 1), actualTime = null, error = null) ?: return null
    return try {
        UIImage.imageWithCGImage(cgImage)
    } finally {
        CGImageRelease(cgImage)
    }
}
