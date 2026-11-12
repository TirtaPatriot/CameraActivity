package id.tirtapatriot.activitycamera.server

import java.time.ZoneId

/** Configuration from environment variables (see server/.env.example). */
data class ServerConfig(
    val port: Int,
    /** NAS share mounted on the server host, e.g. /mnt/nas/activity-camera */
    val storageRoot: String,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val dbPoolSize: Int,
    val maxUploadBytes: Long,
    /** Used once to create the first admin when no admin account exists. */
    val bootstrapAdminUsername: String?,
    val bootstrapAdminPassword: String?,
    /** Public base URL encoded in the connect QR; derived from the request when unset. */
    val publicUrl: String?,
    /** Zone used for date filters and dates shown in the admin panel. */
    val timeZone: ZoneId,
    /** Single sign-on; null when not configured. */
    val oidc: OidcConfig?,
) {
    companion object {
        fun fromEnv(): ServerConfig {
            fun required(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: error("Environment variable $name is required")
            fun optional(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
            fun optional(name: String, default: String) = optional(name) ?: default

            return ServerConfig(
                port = optional("PORT", "8080").toInt(),
                storageRoot = required("STORAGE_ROOT"),
                dbUrl = required("DB_URL"),
                dbUser = required("DB_USER"),
                dbPassword = required("DB_PASSWORD"),
                dbPoolSize = optional("DB_POOL_SIZE", "5").toInt(),
                maxUploadBytes = optional("MAX_UPLOAD_MB", "1024").toLong() * 1024 * 1024,
                bootstrapAdminUsername = optional("ADMIN_USERNAME"),
                bootstrapAdminPassword = optional("ADMIN_PASSWORD"),
                publicUrl = optional("PUBLIC_URL")?.trimEnd('/'),
                timeZone = ZoneId.of(optional("TIMEZONE", "Asia/Jakarta")),
                oidc = oidcFromEnv(::optional),
            )
        }

        private fun oidcFromEnv(optional: (String) -> String?): OidcConfig? {
            val discoveryUrl = optional("OIDC_DISCOVERY_URL")
            val clientId = optional("OIDC_CLIENT_ID")
            val clientSecret = optional("OIDC_CLIENT_SECRET")
            if (discoveryUrl == null && clientId == null && clientSecret == null) return null
            val missing = listOf("OIDC_DISCOVERY_URL" to discoveryUrl, "OIDC_CLIENT_ID" to clientId, "OIDC_CLIENT_SECRET" to clientSecret)
                .filter { it.second == null }.map { it.first }
            require(missing.isEmpty()) { "Single sign-on is partly configured; also set: ${missing.joinToString()}" }
            return OidcConfig(
                discoveryUrl = discoveryUrl!!.let {
                    // Accept the issuer URL too: append the well-known path when it is missing.
                    if (it.contains("/.well-known/")) it else it.trimEnd('/') + "/.well-known/openid-configuration"
                },
                clientId = clientId!!,
                clientSecret = clientSecret!!,
                scopes = optional("OIDC_SCOPES") ?: "openid profile email",
                userCodeClaim = optional("OIDC_USER_CODE_CLAIM"),
                groupsClaim = optional("OIDC_GROUPS_CLAIM") ?: "groups",
                adminGroup = optional("OIDC_ADMIN_GROUP"),
                buttonLabel = optional("OIDC_BUTTON_LABEL"),
            )
        }
    }
}

/** OpenID Connect provider (Casdoor, Keycloak, Authentik, Google, Microsoft Entra, …). */
data class OidcConfig(
    /** e.g. https://sso.example.org/.well-known/openid-configuration */
    val discoveryUrl: String,
    val clientId: String,
    val clientSecret: String,
    val scopes: String,
    /** Claim holding the employee ID (NIP), dotted path allowed, e.g. "properties.nip"; null = not synced. */
    val userCodeClaim: String?,
    /** Claim with the user's groups/roles. */
    val groupsClaim: String,
    /** Members of this group become admins; null = roles are managed in the panel only. */
    val adminGroup: String?,
    val buttonLabel: String?,
) {
    override fun toString() = "OidcConfig(discoveryUrl=$discoveryUrl, clientId=$clientId)" // never print the secret
}
