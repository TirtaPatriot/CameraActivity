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
        // PBKDF2 is deliberately slow; keep it off the request threads.
        return if (blocking { PasswordHasher.verify(password, user.passwordHash) }) user else null
    }

    companion object {
        val PANEL_SESSION: Duration = Duration.ofHours(12)
    }
}
