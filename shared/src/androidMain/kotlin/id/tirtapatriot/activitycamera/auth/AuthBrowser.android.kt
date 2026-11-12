package id.tirtapatriot.activitycamera.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import id.tirtapatriot.activitycamera.model.ApiContract
import kotlinx.coroutines.CompletableDeferred
import java.lang.ref.WeakReference
import java.security.MessageDigest

actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

/**
 * Custom Tabs sign-in. MainActivity (launchMode singleTask, with an intent filter for
 * [ApiContract.OIDC_APP_SCHEME]) forwards the redirect and its pause/resume to this object.
 */
actual object AuthBrowser {
    private var activity: WeakReference<Activity>? = null
    private var pending: CompletableDeferred<String?>? = null
    private var browserShown = false

    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    actual suspend fun open(url: String, callbackScheme: String): String? {
        val host = activity?.get() ?: return null
        pending?.complete(null)
        val result = CompletableDeferred<String?>()
        pending = result
        browserShown = false
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(host, Uri.parse(url))
        return result.await()
    }

    /** From onCreate/onNewIntent; true when the intent was the sign-in redirect. */
    fun onIntent(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (uri.scheme != ApiContract.OIDC_APP_SCHEME) return false
        pending?.complete(uri.toString())
        pending = null
        return true
    }

    fun onPaused() {
        if (pending != null) browserShown = true
    }

    /** Back in the app without a redirect: the officer closed the browser. */
    fun onResumed() {
        if (browserShown) {
            pending?.complete(null)
            pending = null
            browserShown = false
        }
    }
}
