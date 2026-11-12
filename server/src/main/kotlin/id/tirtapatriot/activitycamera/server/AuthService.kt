package id.tirtapatriot.activitycamera.server

import id.tirtapatriot.activitycamera.model.UserRole
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import java.time.Duration
import java.time.Instant

/** Password login for the mobile app (bearer tokens) and the admin panel (cookie tokens). */
class AuthService(private val users: UserDb) {

    /** Returns a new mobile bearer token, or null when the credentials are wrong or the account is disabled. */
    suspend fun loginMobile(username: String, password: String, deviceName: String): Pair<String, UserRecord>? {
        val user = verify(username, password) ?: return null
        val token = Tokens.newToken()
        blocking { users.createToken(user.id, Tokens.hash(token), TokenKind.MOBILE, deviceName, expiresAt = null) }
        return token to user
    }

    /** Returns a panel session token for an active ADMIN, or null. */
    suspend fun loginPanel(username: String, password: String): String? {
        val user = verify(username, password)?.takeIf { it.role == UserRole.ADMIN } ?: return null
        val token = Tokens.newToken()
        val expiresAt = Instant.now().plus(PANEL_SESSION)
        blocking { users.createToken(user.id, Tokens.hash(token), TokenKind.PANEL, "admin-panel", expiresAt) }
        return token
    }

    suspend fun userFromBearer(call: ApplicationCall): UserRecord? {
        val token = bearerToken(call) ?: return null
        return blocking { users.findUserByToken(Tokens.hash(token), TokenKind.MOBILE) }
    }

    suspend fun userFromPanelToken(token: String?): UserRecord? {
        if (token.isNullOrBlank()) return null
        return blocking { users.findUserByToken(Tokens.hash(token), TokenKind.PANEL) }
            ?.takeIf { it.role == UserRole.ADMIN }
    }

    suspend fun revoke(token: String) {
        blocking { users.revokeToken(Tokens.hash(token)) }
    }

    fun bearerToken(call: ApplicationCall): String? =
        call.request.headers[HttpHeaders.Authorization]
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring(7)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private suspend fun verify(username: String, password: String): UserRecord? {
        val user = blocking { users.findByUsername(username.trim().lowercase()) } ?: return null
        if (!user.isActive) return null
        val hash = user.passwordHash ?: return null // SSO-only account
        // PBKDF2 is deliberately slow; keep it off the request threads.
        return if (blocking { PasswordHasher.verify(password, hash) }) user else null
    }

    /**
     * Account for an identity confirmed by the OIDC provider: the linked account, else an unlinked
     * panel account with the same employee ID, else a new officer account. Name, employee ID and
     * (when OIDC_ADMIN_GROUP is set) role follow the provider. Null when the account is disabled.
     */
    suspend fun userFromOidc(identity: OidcIdentity, adminGroup: String?): UserRecord? = blocking {
        val role = adminGroup?.let { if (identity.isInGroup(it)) UserRole.ADMIN else UserRole.OFFICER }
        val existing = users.findByOidc(identity.issuer, identity.subject)
            ?: identity.userCode?.let { code ->
                users.findUnlinkedByUserCode(code)?.also { users.linkOidc(it.id, identity.issuer, identity.subject) }
            }
        val id = if (existing == null) {
            users.createOidc(
                username = uniqueUsername(identity),
                displayName = identity.displayName,
                userCode = identity.userCode,
                role = role ?: UserRole.OFFICER,
                issuer = identity.issuer,
                subject = identity.subject,
            )
        } else {
            users.update(
                existing.id,
                displayName = identity.displayName,
                userCode = identity.userCode ?: existing.userCode,
                role = role ?: existing.role,
            )
            existing.id
        }
        users.findById(id)?.takeIf { it.isActive }
    }

    suspend fun issueMobileToken(user: UserRecord, deviceName: String?): String {
        val token = Tokens.newToken()
        blocking { users.createToken(user.id, Tokens.hash(token), TokenKind.MOBILE, deviceName, expiresAt = null) }
        return token
    }

    /** Panel session for an active admin, or null. */
    suspend fun issuePanelToken(user: UserRecord): String? {
        if (user.role != UserRole.ADMIN || !user.isActive) return null
        val token = Tokens.newToken()
        blocking { users.createToken(user.id, Tokens.hash(token), TokenKind.PANEL, "admin-panel (SSO)", Instant.now().plus(PANEL_SESSION)) }
        return token
    }

    /** Username from the provider (preferred_username, else e-mail), made valid and unique. */
    private fun uniqueUsername(identity: OidcIdentity): String {
        val base = (identity.preferredUsername ?: identity.email?.substringBefore('@') ?: "sso")
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "")
            .take(40)
            .ifEmpty { "sso" }
            .let { if (it.length < 3) "$it-sso" else it }
        if (!users.usernameExists(base)) return base
        var n = 2
        while (users.usernameExists("$base-$n")) n++
        return "$base-$n"
    }

    companion object {
        val PANEL_SESSION: Duration = Duration.ofHours(12)
    }
}
