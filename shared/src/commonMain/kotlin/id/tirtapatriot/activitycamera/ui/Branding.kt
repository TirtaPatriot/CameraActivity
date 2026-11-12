@file:OptIn(ExperimentalResourceApi::class)

package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
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

/** "#1565C0" → Color, or null when malformed. */
fun parseHexColor(hex: String): Color? {
    val digits = hex.removePrefix("#")
    if (digits.length != 6) return null
    val rgb = digits.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}

/** Dark Material theme tinted with the organization's primary color from the server. */
@Composable
fun BrandedTheme(primaryColorHex: String, content: @Composable () -> Unit) {
    val primary = parseHexColor(primaryColorHex) ?: DefaultPrimary
    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
    MaterialTheme(
        colorScheme = darkColorScheme(primary = primary, onPrimary = onPrimary),
        content = content,
    )
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
