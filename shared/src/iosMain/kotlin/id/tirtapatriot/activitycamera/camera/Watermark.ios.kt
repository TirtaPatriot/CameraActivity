@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package id.tirtapatriot.activitycamera.camera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
// Star import: NSString drawing/measuring are UIKit category extensions.
import platform.UIKit.*

/** Burns the watermark into a photo (same layout as the live preview) and saves it as JPEG. */
internal object IosWatermark {
    private const val JPEG_QUALITY = 0.9

    fun burn(data: NSData, spec: WatermarkSpec?, outputPath: String): Boolean {
        val image = UIImage.imageWithData(data) ?: return false
        val size = image.size
        val (width, height) = size.useContents { width to height }

        val format = UIGraphicsImageRendererFormat.defaultFormat()
        format.scale = 1.0 // keep original resolution (pixels = points)
        val renderer = UIGraphicsImageRenderer(size = size, format = format)

        val result = renderer.imageWithActions { _ ->
            image.drawInRect(CGRectMake(0.0, 0.0, width, height)) // applies EXIF orientation
            if (spec != null) draw(spec, width.toFloat(), height.toFloat())
        }
        val jpeg = UIImageJPEGRepresentation(result, JPEG_QUALITY) ?: return false
        return jpeg.writeToFile(outputPath, atomically = true)
    }

    private fun font(sizePx: Float, bold: Boolean): UIFont =
        if (bold) UIFont.boldSystemFontOfSize(sizePx.toDouble()) else UIFont.systemFontOfSize(sizePx.toDouble())

    private fun color(argb: Long): UIColor = UIColor.colorWithRed(
        red = ((argb shr 16) and 0xFF) / 255.0,
        green = ((argb shr 8) and 0xFF) / 255.0,
        blue = (argb and 0xFF) / 255.0,
        alpha = ((argb shr 24) and 0xFF) / 255.0,
    )

    private fun draw(spec: WatermarkSpec, width: Float, height: Float) {
        val measure = TextMeasure { text, sizePx, bold ->
            val attributes: Map<Any?, Any?> = mapOf(NSFontAttributeName to font(sizePx, bold))
            NSString.create(string = text).sizeWithAttributes(attributes).useContents { this.width }.toFloat()
        }
        val layout = WatermarkLayout.layout(spec, width, height, measure)
        layout.ops.forEach { op ->
            when (op) {
                is WatermarkOp.Box -> {
                    color(op.argb).setFill()
                    val rect = CGRectMake(op.left.toDouble(), op.top.toDouble(), (op.right - op.left).toDouble(), (op.bottom - op.top).toDouble())
                    UIBezierPath.bezierPathWithRoundedRect(rect, cornerRadius = op.radius.toDouble()).fill()
                }

                is WatermarkOp.Text -> {
                    val font = font(op.sizePx, op.bold)
                    val attributes = mutableMapOf<Any?, Any?>(
                        NSFontAttributeName to font,
                        NSForegroundColorAttributeName to color(op.argb),
                    )
                    if (op.shadow) {
                        attributes[NSShadowAttributeName] = NSShadow().apply {
                            shadowColor = UIColor.blackColor.colorWithAlphaComponent(0.6)
                            shadowBlurRadius = op.sizePx * 0.12
                            shadowOffset = CGSizeMake(1.0, 1.0)
                        }
                    }
                    val top = op.top + (op.lineHeight - font.lineHeight.toFloat()) / 2f
                    NSString.create(string = op.text).drawAtPoint(CGPointMake(op.left.toDouble(), top.toDouble()), withAttributes = attributes)
                }
            }
        }
    }
}
