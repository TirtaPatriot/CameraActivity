package id.tirtapatriot.activitycamera.server

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Who the provider says signed in. */
data class OidcIdentity(
    val issuer: String,
    val subject: String,
    val displayName: String,
    val preferredUsername: String?,
    val email: String?,
    val userCode: String?,
    val groups: List<String>,
) {
    /** Exact match, or the last path segment (Casdoor uses "organization/group"). */
    fun isInGroup(group: String): Boolean = groups.any { it == group || it.substringAfterLast('/') == group }
}

enum class OidcClient { MOBILE, PANEL }

/** A sign-on in progress, kept between /auth/oidc/start and the provider's callback. */
data class OidcPending(
    val state: String,
    val nonce: String,
    val providerCodeVerifier: String,
    val redirectUri: String,
    val client: OidcClient,
    /** Mobile only: the app's own state and PKCE challenge, and its device name. */
    val appState: String?,
    val appCodeChallenge: String?,
    val deviceName: String?,
    val createdAt: Instant = Instant.now(),
)

class OidcException(message: String) : Exception(message)

/**
 * OpenID Connect "authorization code" flow with the server as a confidential client.
 * The client secret never leaves the server; the app gets our own bearer token afterwards.
 */
class OidcService(private val config: OidcConfig) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val pending = ConcurrentHashMap<String, OidcPending>()
    private val appCodes = ConcurrentHashMap<String, AppCode>()

    @Volatile
    private var provider: Provider? = null

    val buttonLabel: String? get() = config.buttonLabel

    private class Provider(
        val issuer: String,
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val userinfoEndpoint: String?,
        val useBasicAuth: Boolean,
        val processor: DefaultJWTProcessor<SecurityContext>,
    )

    private data class AppCode(val userId: Long, val codeChallenge: String, val deviceName: String?, val expiresAt: Instant)

    /** Fetches (once) the discovery document; failures are retried on the next sign-in. */
    private fun provider(): Provider {
        provider?.let { return it }
        val document = getJson(config.discoveryUrl)
        fun field(name: String) = document[name]?.jsonPrimitive?.contentOrNull
        val issuer = field("issuer") ?: throw OidcException("Discovery document has no issuer")
        val jwksUri = field("jwks_uri") ?: throw OidcException("Discovery document has no jwks_uri")
        val authMethods = (document["token_endpoint_auth_methods_supported"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        val algorithms = ((document["id_token_signing_alg_values_supported"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: listOf("RS256"))
            .map(JWSAlgorithm::parse)
            .filter { it in SIGNATURE_ALGORITHMS }
            .ifEmpty { listOf(JWSAlgorithm.RS256) }

        val processor = DefaultJWTProcessor<SecurityContext>().apply {
            setJWSKeySelector(JWSVerificationKeySelector(algorithms.toSet(), JWKSourceBuilder.create<SecurityContext>(URI(jwksUri).toURL()).build()))
            // issuer + audience exact, signature + expiry checked by the processor
            setJWTClaimsSetVerifier(
                DefaultJWTClaimsVerifier(
                    config.clientId,
                    JWTClaimsSet.Builder().issuer(issuer).build(),
                    setOf("sub", "exp", "iat"),
                ),
            )
        }
        return Provider(
            issuer = issuer,
            authorizationEndpoint = field("authorization_endpoint") ?: throw OidcException("Discovery document has no authorization_endpoint"),
            tokenEndpoint = field("token_endpoint") ?: throw OidcException("Discovery document has no token_endpoint"),
            userinfoEndpoint = field("userinfo_endpoint"),
            // RFC 8414: client_secret_basic is the default when the list is absent.
            useBasicAuth = authMethods == null || "client_secret_basic" in authMethods || "client_secret_post" !in authMethods,
            processor = processor,
        ).also { provider = it }
    }

    /** URL of the provider's sign-in page for a new sign-on. */
    fun start(client: OidcClient, redirectUri: String, appState: String?, appCodeChallenge: String?, deviceName: String?): String {
        purgeExpired()
        val p = provider()
        val login = OidcPending(
            state = Tokens.newToken(),
            nonce = Tokens.newToken(),
            providerCodeVerifier = Tokens.newToken(),
            redirectUri = redirectUri,
            client = client,
            appState = appState,
            appCodeChallenge = appCodeChallenge,
            deviceName = deviceName?.take(100),
        )
        pending[login.state] = login
        val query = linkedMapOf(
            "response_type" to "code",
            "client_id" to config.clientId,
            "redirect_uri" to redirectUri,
            "scope" to config.scopes,
            "state" to login.state,
            "nonce" to login.nonce,
            "code_challenge" to s256(login.providerCodeVerifier),
            "code_challenge_method" to "S256",
        )
        val separator = if ('?' in p.authorizationEndpoint) '&' else '?'
        return p.authorizationEndpoint + separator + query.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
    }

    /** Takes the sign-on started with [state] (one use only). */
    fun takePending(state: String?): OidcPending? {
        if (state.isNullOrBlank()) return null
        purgeExpired()
        return pending.remove(state)
    }

    /** Exchanges the provider's code and validates the ID token (signature, issuer, audience, expiry, nonce). */
    fun identify(login: OidcPending, code: String): OidcIdentity {
        val p = provider()
        val form = buildMap {
            put("grant_type", "authorization_code")
            put("code", code)
            put("redirect_uri", login.redirectUri)
            put("code_verifier", login.providerCodeVerifier)
            put("client_id", config.clientId)
            if (!p.useBasicAuth) put("client_secret", config.clientSecret)
        }
        val request = HttpRequest.newBuilder(URI(p.tokenEndpoint))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .apply {
                if (p.useBasicAuth) {
                    val credentials = "${encode(config.clientId)}:${encode(config.clientSecret)}"
                    header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray()))
                }
            }
            .POST(HttpRequest.BodyPublishers.ofString(form.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw OidcException("Token endpoint returned HTTP ${response.statusCode()}")
        val tokens = json.parseToJsonElement(response.body()).jsonObject
        val idToken = tokens["id_token"]?.jsonPrimitive?.contentOrNull ?: throw OidcException("No id_token in the token response")
        val accessToken = tokens["access_token"]?.jsonPrimitive?.contentOrNull

        val claims = try {
            p.processor.process(idToken, null)
        } catch (e: Exception) {
            throw OidcException("Invalid ID token: ${e.message}")
        }
        if (claims.getStringClaim("nonce") != login.nonce) throw OidcException("ID token nonce mismatch")

        val idClaims = json.parseToJsonElement(claims.toString()).jsonObject
        // Some providers only put profile fields in userinfo; ID token claims win when both exist.
        val userinfo = if (accessToken != null && p.userinfoEndpoint != null) {
            runCatching { getJson(p.userinfoEndpoint, bearer = accessToken) }.getOrNull()
                ?.takeIf { it["sub"]?.jsonPrimitive?.contentOrNull == claims.subject }
        } else {
            null
        }
        fun claim(path: String): JsonElement? = lookup(idClaims, path) ?: userinfo?.let { lookup(it, path) }
        fun text(path: String): String? = (claim(path) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        val preferredUsername = text("preferred_username") ?: text("name").takeIf { text("displayName") != null }
        return OidcIdentity(
            issuer = p.issuer,
            subject = claims.subject,
            // Casdoor: "name" is the username and "displayName" the full name.
            displayName = (text("displayName") ?: text("name") ?: preferredUsername ?: text("email") ?: claims.subject).take(100),
            preferredUsername = preferredUsername,
            email = text("email"),
            userCode = config.userCodeClaim?.let(::text)?.take(50),
            groups = when (val value = claim(config.groupsClaim)) {
                is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(value.contentOrNull)
                else -> emptyList()
            },
        )
    }

    /** One-time code handed to the app through its redirect; valid for two minutes. */
    fun issueAppCode(userId: Long, login: OidcPending): String {
        val code = Tokens.newToken()
        appCodes[code] = AppCode(userId, login.appCodeChallenge.orEmpty(), login.deviceName, Instant.now().plusSeconds(120))
        return code
    }

    /** User id and device name for a valid code whose PKCE verifier matches; the code is consumed. */
    fun redeemAppCode(code: String, codeVerifier: String): Pair<Long, String?>? {
        purgeExpired()
        val entry = appCodes.remove(code) ?: return null
        if (entry.expiresAt.isBefore(Instant.now())) return null
        val expected = entry.codeChallenge.toByteArray()
        val actual = s256(codeVerifier).toByteArray()
        if (expected.isEmpty() || !MessageDigest.isEqual(expected, actual)) return null
        return entry.userId to entry.deviceName
    }

    private fun purgeExpired() {
        val cutoff = Instant.now().minus(PENDING_TTL)
        pending.values.removeIf { it.createdAt.isBefore(cutoff) }
        val now = Instant.now()
        appCodes.values.removeIf { it.expiresAt.isBefore(now) }
    }

    private fun getJson(url: String, bearer: String? = null): JsonObject {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw OidcException("GET $url returned HTTP ${response.statusCode()}")
        return json.parseToJsonElement(response.body()).jsonObject
    }

    /** Claim by dotted path, e.g. "properties.nip". */
    private fun lookup(root: JsonObject, path: String): JsonElement? {
        root[path]?.let { return it.takeUnless { it is JsonNull } }
        var current: JsonElement = root
        for (part in path.split('.')) {
            current = (current as? JsonObject)?.get(part) ?: return null
        }
        return current.takeUnless { it is JsonNull }
    }

    companion object {
        private val PENDING_TTL: Duration = Duration.ofMinutes(10)
        private val SIGNATURE_ALGORITHMS = setOf(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512,
        )

        /** PKCE S256: base64url(SHA-256(verifier)) without padding. */
        fun s256(verifier: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
    }
}
