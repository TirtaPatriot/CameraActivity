package id.tirtapatriot.activitycamera.server

import id.tirtapatriot.activitycamera.model.ApiContract
import id.tirtapatriot.activitycamera.model.ErrorResponse
import id.tirtapatriot.activitycamera.model.LoginResponse
import id.tirtapatriot.activitycamera.model.OidcTokenRequest
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Cookie set by the panel login page; must match the session cookie name in AdminRoutes. */
const val PANEL_COOKIE = "ac_panel"
private const val STATE_COOKIE = "ac_oidc"
private val pkceChallengePattern = Regex("^[A-Za-z0-9_-]{43}$")
private val appStatePattern = Regex("^[A-Za-z0-9_-]{8,128}$")

/** Base URL the phones and the provider should use, e.g. https://camera.example.org */
fun ApplicationCall.publicBaseUrl(config: ServerConfig): String {
    config.publicUrl?.let { return it }
    val origin = request.origin
    val defaultPort = (origin.scheme == "https" && origin.serverPort == 443) || (origin.scheme == "http" && origin.serverPort == 80)
    return buildString {
        append(origin.scheme).append("://").append(origin.serverHost)
        if (!defaultPort) append(':').append(origin.serverPort)
    }
}

/**
 * Single sign-on: /auth/oidc/start → provider → /auth/oidc/callback.
 * The panel gets its session cookie; the app gets a one-time code (PKCE-bound) via its URL scheme.
 */
fun Route.oidcRoutes(deps: ServerDeps) {
    val oidc = deps.oidc ?: return

    get(ApiContract.OIDC_START) {
        val query = call.request.queryParameters
        val isMobile = query["client"] == "mobile"
        val redirectUri = call.publicBaseUrl(deps.config) + ApiContract.OIDC_CALLBACK
        val url = try {
            if (isMobile) {
                val appState = query["state"]?.takeIf { appStatePattern.matches(it) }
                val challenge = query["code_challenge"]?.takeIf { pkceChallengePattern.matches(it) }
                if (appState == null || challenge == null || query["code_challenge_method"] != "S256") {
                    return@get call.respondText("Invalid sign-in request", status = HttpStatusCode.BadRequest)
                }
                oidc.start(OidcClient.MOBILE, redirectUri, appState, challenge, query["device"])
            } else {
                oidc.start(OidcClient.PANEL, redirectUri, null, null, null).also { providerUrl ->
                    // Binds the callback to this browser (no login CSRF for the panel).
                    val state = Regex("[?&]state=([^&]+)").find(providerUrl)?.groupValues?.get(1).orEmpty()
                    call.response.cookies.append(
                        Cookie(
                            name = STATE_COOKIE, value = state, path = ApiContract.OIDC_CALLBACK, maxAge = 600,
                            httpOnly = true, secure = call.request.origin.scheme == "https", extensions = mapOf("SameSite" to "Lax"),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            call.application.environment.log.warn("SSO start failed: ${e.message}")
            return@get call.respondText("Single sign-on is unavailable right now", status = HttpStatusCode.BadGateway)
        }
        call.respondRedirect(url)
    }

    get(ApiContract.OIDC_CALLBACK) {
        val query = call.request.queryParameters
        val login = oidc.takePending(query["state"])
            ?: return@get call.respondText("This sign-in link has expired. Please start again.", status = HttpStatusCode.BadRequest)

        fun appRedirect(params: String) = ApiContract.OIDC_APP_REDIRECT + "?" + params + "&state=" + login.appState.orEmpty()

        if (login.client == OidcClient.PANEL && call.request.cookies[STATE_COOKIE] != login.state) {
            return@get call.respondRedirect("/admin/login?sso=failed")
        }
        val code = query["code"]
        if (code == null) {
            // The provider reported an error (e.g. the user cancelled).
            return@get when (login.client) {
                OidcClient.PANEL -> call.respondRedirect("/admin/login?sso=failed")
                OidcClient.MOBILE -> call.backToApp(appRedirect("error=" + (query["error"] ?: "access_denied").encodeURLParameter()))
            }
        }

        val user = try {
            val identity = blocking { oidc.identify(login, code) }
            deps.auth.userFromOidc(identity, deps.config.oidc?.adminGroup)
        } catch (e: Exception) {
            call.application.environment.log.warn("SSO callback failed: ${e.message}")
            null
        }

        when (login.client) {
            OidcClient.PANEL -> {
                val token = user?.let { deps.auth.issuePanelToken(it) }
                    ?: return@get call.respondRedirect("/admin/login?sso=" + if (user == null) "failed" else "notadmin")
                call.response.cookies.append(
                    Cookie(
                        name = PANEL_COOKIE, value = token, path = "/admin",
                        maxAge = AuthService.PANEL_SESSION.seconds.toInt(), httpOnly = true,
                        secure = call.request.origin.scheme == "https", extensions = mapOf("SameSite" to "Strict"),
                    ),
                )
                call.response.cookies.append(Cookie(name = STATE_COOKIE, value = "", path = ApiContract.OIDC_CALLBACK, maxAge = 0))
                // A same-site hop: a SameSite=Strict cookie is not sent on a redirect chain that began at the provider.
                call.respondText(
                    """<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=/admin"><a href="/admin">Continue</a>""",
                    ContentType.Text.Html,
                )
            }

            OidcClient.MOBILE -> {
                val params = if (user == null) "error=access_denied" else "code=" + oidc.issueAppCode(user.id, login)
                call.backToApp(appRedirect(params))
            }
        }
    }

    // The app trades the one-time code (plus its PKCE verifier) for a bearer token.
    post(ApiContract.OIDC_TOKEN) {
        val request = call.receive<OidcTokenRequest>()
        val (userId, deviceName) = oidc.redeemAppCode(request.code, request.codeVerifier)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or expired code"))
        val user = blocking { deps.users.findById(userId) }?.takeIf { it.isActive }
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Account disabled"))
        val token = deps.auth.issueMobileToken(user, deviceName)
        call.respond(LoginResponse(token, user.toProfile()))
    }
}

/** Opens the app; the page also has a button in case the browser blocks the automatic switch. */
private suspend fun ApplicationCall.backToApp(url: String) {
    val safe = url.replace("&", "&amp;").replace("\"", "&quot;")
    respondText(
        """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <meta http-equiv="refresh" content="0;url=$safe"><title>Activity Camera</title>
        <style>body{font:16px system-ui,sans-serif;display:flex;min-height:90vh;align-items:center;justify-content:center;background:#f5f6f8}
        a{background:#1565C0;color:#fff;padding:14px 22px;border-radius:12px;text-decoration:none}</style></head>
        <body><a href="$safe">Kembali ke aplikasi / Back to the app</a></body></html>
        """.trimIndent(),
        ContentType.Text.Html,
    )
}
