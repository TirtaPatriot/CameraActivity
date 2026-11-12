package id.tirtapatriot.activitycamera.sync

import id.tirtapatriot.activitycamera.model.ApiContract
import id.tirtapatriot.activitycamera.model.AppConfig
import id.tirtapatriot.activitycamera.model.ConnectPayload
import id.tirtapatriot.activitycamera.model.LoginRequest
import id.tirtapatriot.activitycamera.model.LoginResponse
import id.tirtapatriot.activitycamera.model.OidcTokenRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class UnauthorizedException : Exception("Unauthorized")

/** HTTP calls to the organization's server. One instance is shared by the whole app. */
class ServerApi {
    internal val json = Json { ignoreUnknownKeys = true }

    internal val http = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
            requestTimeoutMillis = 30 * 60_000L
        }
    }

    suspend fun fetchConfig(serverUrl: String): AppConfig {
        val response = http.get(serverUrl + ApiContract.CONFIG)
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        return response.body()
    }

    suspend fun fetchLogo(serverUrl: String, version: Long): ByteArray? {
        val response = http.get("$serverUrl${ApiContract.LOGO}?v=$version")
        return if (response.status.isSuccess()) response.readRawBytes() else null
    }

    /** Null when the credentials are rejected. */
    suspend fun login(serverUrl: String, username: String, password: String, deviceName: String): LoginResponse? {
        val response = http.post(serverUrl + ApiContract.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username.trim().lowercase(), password, deviceName))
        }
        if (response.status == HttpStatusCode.Unauthorized) return null
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        return response.body()
    }

    /** Page to open in the browser to start single sign-on (PKCE: only the challenge leaves the phone). */
    fun oidcStartUrl(serverUrl: String, state: String, codeChallenge: String, deviceName: String): String =
        serverUrl + ApiContract.OIDC_START + "?client=mobile&state=" + state.encodeURLParameter() +
            "&code_challenge=" + codeChallenge.encodeURLParameter() + "&code_challenge_method=S256" +
            "&device=" + deviceName.encodeURLParameter()

    /** Trades the one-time code from the browser for a bearer token; null when it is rejected. */
    suspend fun exchangeOidcCode(serverUrl: String, code: String, codeVerifier: String): LoginResponse? {
        val response = http.post(serverUrl + ApiContract.OIDC_TOKEN) {
            contentType(ContentType.Application.Json)
            setBody(OidcTokenRequest(code, codeVerifier))
        }
        if (response.status == HttpStatusCode.Unauthorized) return null
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        return response.body()
    }

    suspend fun logout(serverUrl: String, token: String) {
        runCatching { http.post(serverUrl + ApiContract.LOGOUT) { bearerAuth(token) } }
    }

    companion object {
        /** "camera.example.org" → "https://camera.example.org"; trailing slashes removed. */
        fun normalizeUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
            val host = withScheme.substringAfter("://").substringBefore('/')
            return withScheme.takeIf { host.isNotEmpty() }
        }

        /** Server URL from a scanned QR: the JSON connect payload or a bare URL. */
        fun serverUrlFromQr(content: String, json: Json = Json { ignoreUnknownKeys = true }): String? {
            val payload = runCatching { json.decodeFromString(ConnectPayload.serializer(), content) }.getOrNull()
            if (payload != null) return payload.serverUrl.takeIf { payload.type == ConnectPayload.TYPE }?.let(::normalizeUrl)
            return content.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let(::normalizeUrl)
        }
    }
}
