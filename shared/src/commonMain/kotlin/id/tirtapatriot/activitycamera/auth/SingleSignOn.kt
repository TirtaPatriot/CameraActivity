@file:OptIn(ExperimentalEncodingApi::class)

package id.tirtapatriot.activitycamera.auth

import id.tirtapatriot.activitycamera.model.ApiContract
import id.tirtapatriot.activitycamera.model.LoginResponse
import id.tirtapatriot.activitycamera.platform.PlatformInfo
import id.tirtapatriot.activitycamera.sync.ServerApi
import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

/**
 * Opens [url] in the system browser (Custom Tabs / ASWebAuthenticationSession, sharing the browser's
 * SSO session) and returns the URL the server redirected to with [callbackScheme], or null when cancelled.
 */
expect object AuthBrowser {
    suspend fun open(url: String, callbackScheme: String): String?
}

expect fun sha256(data: ByteArray): ByteArray

sealed interface SsoResult {
    data class Success(val response: LoginResponse) : SsoResult
    data object Cancelled : SsoResult
    data object Rejected : SsoResult
}

/** Single sign-on through the server (which talks OIDC to the organization's provider). */
object SingleSignOn {
    suspend fun signIn(api: ServerApi, serverUrl: String): SsoResult {
        // PKCE: the verifier never leaves the phone; a stolen redirect code is useless without it.
        val verifier = Uuid.random().toHexString() + Uuid.random().toHexString()
        val challenge = Base64.UrlSafe.encode(sha256(verifier.encodeToByteArray())).trimEnd('=')
        val state = Uuid.random().toHexString()

        val callback = AuthBrowser.open(
            api.oidcStartUrl(serverUrl, state, challenge, PlatformInfo.deviceModel),
            ApiContract.OIDC_APP_SCHEME,
        ) ?: return SsoResult.Cancelled
        val parameters = Url(callback).parameters
        if (parameters["state"] != state) return SsoResult.Rejected
        val code = parameters["code"] ?: return if (parameters["error"] == "access_denied") SsoResult.Rejected else SsoResult.Cancelled
        val response = api.exchangeOidcCode(serverUrl, code, verifier) ?: return SsoResult.Rejected
        return SsoResult.Success(response)
    }
}
