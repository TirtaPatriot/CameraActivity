package id.tirtapatriot.activitycamera.server.admin

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Renders a QR code as a crisp inline SVG (no image files needed). */
object QrSvg {
    fun render(text: String, displaySize: Int = 260): String {
        val hints = mapOf(
            EncodeHintType.MARGIN to 2,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        // Width/height 0 → the smallest matrix that fits; the SVG viewBox scales it.
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = matrix.width
        val path = StringBuilder()
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (matrix.get(x, y)) path.append("M").append(x).append(' ').append(y).append("h1v1h-1z")
            }
        }
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $size $size" width="$displaySize" """ +
            """height="$displaySize" shape-rendering="crispEdges" role="img">""" +
            """<rect width="$size" height="$size" fill="#fff"/><path fill="#000" d="$path"/></svg>"""
    }
}
