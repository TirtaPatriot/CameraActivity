@file:OptIn(ExperimentalResourceApi::class)

package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.tirtapatriot.activitycamera.platform.mediaDirectory
import id.tirtapatriot.activitycamera.sync.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

private val DefaultPrimary = Color(0xFF1565C0)

/** Light palette for every screen except the camera. */
object Palette {
    val Background = Color(0xFFF5F6F8)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFEEF0F3)
    val Text = Color(0xFF1D2330)
    val TextMuted = Color(0xFF5B6474)
    val Outline = Color(0xFFDDE1E7)
    val Success = Color(0xFF2E7D32)
    val Warning = Color(0xFFB26A00)
    val WarningSoft = Color(0xFFFFF4E5)
    val Danger = Color(0xFFC62828)

    /** Recording / video accent. */
    val Record = Color(0xFFE53935)
}

/** "#1565C0" → Color, or null when malformed. */
fun parseHexColor(hex: String): Color? {
    val digits = hex.removePrefix("#")
    if (digits.length != 6) return null
    val rgb = digits.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}

fun brandColor(primaryColorHex: String): Color = parseHexColor(primaryColorHex) ?: DefaultPrimary

/** Light Material theme tinted with the organization's primary color from the server. */
@Composable
fun BrandedTheme(primaryColorHex: String, content: @Composable () -> Unit) {
    val primary = brandColor(primaryColorHex)
    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.12f),
            onPrimaryContainer = primary,
            secondaryContainer = primary.copy(alpha = 0.12f),
            onSecondaryContainer = primary,
            background = Palette.Background,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceMuted,
            onSurfaceVariant = Palette.TextMuted,
            surfaceContainerHighest = Palette.SurfaceMuted,
            outline = Palette.Outline,
            outlineVariant = Palette.Outline,
            error = Palette.Danger,
        ),
        content = content,
    )
}

/**
 * Frosted glass for controls floating over the live camera: translucent smoke, a soft top
 * highlight and a thin light rim. (A real backdrop blur over the camera surface would cost a
 * full-frame GPU pass on every preview frame, so the frost is approximated.)
 */
fun Modifier.glass(shape: Shape = CircleShape, strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(Color(0xFF1C2028).copy(alpha = 0.38f * strength), shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)), shape)
    .border(1.dp, Color.White.copy(alpha = 0.32f), shape)

/** Light frosted panel (sheets over the camera). */
fun Modifier.lightGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.90f), shape)
    .border(1.dp, Color.White.copy(alpha = 0.7f), shape)

/** Round glass button area of [size]. */
@Composable
fun GlassCircle(size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.size(size).glass(CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) { content() }
}

/** Organization logo from the server, cached on disk per logo version. Renders nothing without a logo. */
@Composable
fun BrandLogo(serverUrl: String, logoVersion: Long?, api: ServerApi, modifier: Modifier = Modifier) {
    if (logoVersion == null || serverUrl.isBlank()) return
    val bitmap by produceState<ImageBitmap?>(null, serverUrl, logoVersion) {
        value = loadLogo(serverUrl, logoVersion, api)
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit) }
}

private suspend fun loadLogo(serverUrl: String, version: Long, api: ServerApi): ImageBitmap? =
    withContext(Dispatchers.Default) {
        val path = Path("${mediaDirectory()}/logo-$version")
        val cached = if (SystemFileSystem.exists(path)) {
            runCatching { SystemFileSystem.source(path).buffered().use { it.readByteArray() } }.getOrNull()
        } else {
            null
        }
        val bytes = cached ?: runCatching { api.fetchLogo(serverUrl, version) }.getOrNull()?.also { downloaded ->
            runCatching { SystemFileSystem.sink(path).buffered().use { it.write(downloaded) } }
        }
        bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }
