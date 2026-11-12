@file:OptIn(ExperimentalForeignApi::class)

package id.tirtapatriot.activitycamera.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

actual fun sha256(data: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    data.usePinned { input ->
        digest.usePinned { output ->
            CC_SHA256(input.addressOf(0), data.size.convert(), output.addressOf(0))
        }
    }
    return digest.asByteArray()
}

/** ASWebAuthenticationSession: shares cookies with Safari, so an existing SSO session is reused. */
actual object AuthBrowser {
    // Must stay referenced while the sheet is open.
    private var session: ASWebAuthenticationSession? = null
    private val presenter = Presenter()

    actual suspend fun open(url: String, callbackScheme: String): String? {
        val target = NSURL.URLWithString(url) ?: return null
        return suspendCancellableCoroutine { continuation ->
            val authSession = ASWebAuthenticationSession(target, callbackScheme) { callbackUrl, _ ->
                session = null
                if (continuation.isActive) continuation.resume(callbackUrl?.absoluteString)
            }
            authSession.presentationContextProvider = presenter
            authSession.prefersEphemeralWebBrowserSession = false
            session = authSession
            continuation.invokeOnCancellation { authSession.cancel() }
            if (!authSession.start()) {
                session = null
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private class Presenter : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
        @Suppress("DEPRECATION")
        override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
            UIApplication.sharedApplication.keyWindow ?: UIWindow()
    }
}
