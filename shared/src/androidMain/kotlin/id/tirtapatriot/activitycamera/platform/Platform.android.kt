package id.tirtapatriot.activitycamera.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

/** Called once from MainActivity before setContent. */
@SuppressLint("StaticFieldLeak")
object AndroidPlatform {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

actual object PlatformInfo {
    actual val name: String = "android"
    actual val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    actual val videoExtension: String = "mp4"
}

actual fun mediaDirectory(): String {
    val context = AndroidPlatform.appContext
    // App-specific folder: no storage permission needed, removed on uninstall.
    val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
    return File(base, "ActivityCamera").apply { mkdirs() }.absolutePath
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
