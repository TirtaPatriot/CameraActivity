@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package id.tirtapatriot.activitycamera.camera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.drawAtPoint
import platform.UIKit.sizeWithAttributes
import kotlin.math.min

/** Burns the stamp text into a photo and saves it as JPEG. */
internal object IosWatermark {
    private const val JPEG_QUALITY = 0.92

    fun burn(data: NSData, stamp: Stamp, outputPath: String): Boolean {
        val image = UIImage.imageWithData(data) ?: return false
        val size = image.size
        val (width, height) = size.useContents { width to height }

        val format = UIGraphicsImageRendererFormat.defaultFormat()
        format.scale = 1.0 // keep original resolution (pixels = points)
        val renderer = UIGraphicsImageRenderer(size = size, format = format)

        val result = renderer.imageWithActions { _ ->
            image.drawInRect(CGRectMake(0.0, 0.0, width, height)) // applies EXIF orientation
            drawStamp(stamp.lines, width, height)
        }
        val jpeg = UIImageJPEGRepresentation(result, JPEG_QUALITY) ?: return false
        return jpeg.writeToFile(outputPath, atomically = true)
    }

    private fun drawStamp(lines: List<String>, width: Double, height: Double) {
        val fontSize = min(width, height) * 0.032
        val attributes: Map<Any?, Any?> = mapOf(
            NSFontAttributeName to UIFont.boldSystemFontOfSize(fontSize),
            NSForegroundColorAttributeName to UIColor.whiteColor,
        )
        val nsLines = lines.map { NSString.create(string = it) }
        val padding = fontSize * 0.6
        val lineHeight = fontSize * 1.3
        val maxLineWidth = nsLines.maxOf { line -> line.sizeWithAttributes(attributes).useContents { this.width } }
        val boxWidth = maxLineWidth + padding * 2
        val boxHeight = lineHeight * lines.size + padding * 2
        val left = padding
        val top = height - boxHeight - padding

        UIColor.blackColor.colorWithAlphaComponent(0.45).setFill()
        UIBezierPath.bezierPathWithRoundedRect(CGRectMake(left, top, boxWidth, boxHeight), cornerRadius = padding).fill()
        nsLines.forEachIndexed { index, line ->
            line.drawAtPoint(CGPointMake(left + padding, top + padding + index * lineHeight), withAttributes = attributes)
        }
    }
}
