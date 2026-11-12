package id.tirtapatriot.activitycamera.media

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import id.tirtapatriot.activitycamera.platform.AndroidPlatform
import java.io.File

actual object ShareSheet {
    actual fun share(files: List<ShareFile>, text: String, onShared: () -> Unit) {
        val context = AndroidPlatform.appContext
        val authority = "${context.packageName}.files"
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, authority, File(it.path)) })
        if (uris.isEmpty()) return
        val mimeType = files.map { it.mimeType }.distinct().singleOrNull()
            ?: if (files.all { it.mimeType.startsWith("image/") }) "image/*" else "*/*"

        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }.apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri(null, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
        // Android doesn't report which app was picked without extra plumbing; opening the sheet counts as sharing.
        onShared()
    }
}
