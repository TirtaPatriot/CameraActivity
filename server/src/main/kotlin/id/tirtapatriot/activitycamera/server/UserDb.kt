package id.tirtapatriot.activitycamera.server

import id.tirtapatriot.activitycamera.model.UserProfile
import id.tirtapatriot.activitycamera.model.UserRole
import java.sql.ResultSet
import java.time.Instant
import javax.sql.DataSource

enum class TokenKind { MOBILE, PANEL }

data class UserRecord(
    val id: Long,
    val username: String,
    val displayName: String,
    val userCode: String?,
    val role: UserRole,
    /** Null for accounts that only sign in through SSO. */
    val passwordHash: String?,
    val isActive: Boolean,
    /** Linked identity at the OIDC provider, if any. */
    val oidcIssuer: String? = null,
    val oidcSubject: String? = null,
) {
    val isSsoLinked: Boolean get() = oidcSubject != null

    fun toProfile() = UserProfile(id, username, displayName, userCode, role)
}

/** Accounts (cactivity.app_user) and login tokens (cactivity.auth_token). */
class UserDb(private val dataSource: DataSource) {

    private val userColumns = "u.id, u.username, u.display_name, u.user_code, u.role, u.password_hash, u.is_active, u.oidc_issuer, u.oidc_subject"

    fun countActiveAdmins(): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT COUNT(*) FROM cactivity.app_user WHERE role = 'ADMIN' AND is_active = 1",
        ).use { statement -> statement.executeQuery().use { rs -> rs.next(); rs.getInt(1) } }
    }

    fun findByUsername(username: String): UserRecord? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT $userColumns FROM cactivity.app_user u WHERE u.username = ?").use { statement ->
            statement.setString(1, username)
            statement.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    fun findById(id: Long): UserRecord? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT $userColumns FROM cactivity.app_user u WHERE u.id = ?").use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    fun findByOidc(issuer: String, subject: String): UserRecord? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT $userColumns FROM cactivity.app_user u WHERE u.oidc_issuer = ? AND u.oidc_subject = ?",
        ).use { statement ->
            statement.setString(1, issuer)
            statement.setString(2, subject)
            statement.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    /** An account made in the panel with this employee ID that is not linked to SSO yet (only when unique). */
    fun findUnlinkedByUserCode(userCode: String): UserRecord? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT TOP (2) $userColumns FROM cactivity.app_user u WHERE u.user_code = ? AND u.oidc_subject IS NULL",
        ).use { statement ->
            statement.setString(1, userCode)
            statement.executeQuery().use { rs ->
                val found = buildList { while (rs.next()) add(rs.toUser()) }
                found.singleOrNull()
            }
        }
    }

    fun usernameExists(username: String): Boolean = findByUsername(username) != null

    fun linkOidc(id: Long, issuer: String, subject: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE cactivity.app_user SET oidc_issuer = ?, oidc_subject = ?, updated_at = SYSUTCDATETIME() WHERE id = ?",
            ).use { statement ->
                statement.setString(1, issuer)
                statement.setString(2, subject)
                statement.setLong(3, id)
                statement.executeUpdate()
            }
        }
    }

    /** New account from a first SSO sign-in; it has no password. */
    fun createOidc(username: String, displayName: String, userCode: String?, role: UserRole, issuer: String, subject: String): Long =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO cactivity.app_user (username, display_name, user_code, role, password_hash, oidc_issuer, oidc_subject)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, NULL, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, username)
                statement.setString(2, displayName)
                statement.setNullableString(3, userCode)
                statement.setString(4, role.name)
                statement.setString(5, issuer)
                statement.setString(6, subject)
                statement.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
        }

    fun list(): List<UserRecord> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT $userColumns FROM cactivity.app_user u ORDER BY u.role, u.display_name",
        ).use { statement -> statement.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toUser()) } } }
    }

    fun create(username: String, displayName: String, userCode: String?, role: UserRole, passwordHash: String): Long =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO cactivity.app_user (username, display_name, user_code, role, password_hash)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, username)
                statement.setString(2, displayName)
                statement.setNullableString(3, userCode)
                statement.setString(4, role.name)
                statement.setString(5, passwordHash)
                statement.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
        }

    fun update(id: Long, displayName: String, userCode: String?, role: UserRole) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE cactivity.app_user
                SET display_name = ?, user_code = ?, role = ?, updated_at = SYSUTCDATETIME()
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, displayName)
                statement.setNullableString(2, userCode)
                statement.setString(3, role.name)
                statement.setLong(4, id)
                statement.executeUpdate()
            }
        }
    }

    fun setActive(id: Long, active: Boolean) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE cactivity.app_user SET is_active = ?, updated_at = SYSUTCDATETIME() WHERE id = ?",
            ).use { statement ->
                statement.setBoolean(1, active)
                statement.setLong(2, id)
                statement.executeUpdate()
            }
        }
    }

    fun setPassword(id: Long, passwordHash: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE cactivity.app_user SET password_hash = ?, updated_at = SYSUTCDATETIME() WHERE id = ?",
            ).use { statement ->
                statement.setString(1, passwordHash)
                statement.setLong(2, id)
                statement.executeUpdate()
            }
        }
    }

    fun createToken(userId: Long, tokenHash: String, kind: TokenKind, deviceName: String?, expiresAt: Instant?) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO cactivity.auth_token (token_hash, user_id, kind, device_name, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tokenHash)
                statement.setLong(2, userId)
                statement.setString(3, kind.name)
                statement.setNullableString(4, deviceName?.take(100))
                if (expiresAt == null) statement.setNull(5, java.sql.Types.TIMESTAMP) else statement.setTimestamp(5, expiresAt.toUtcTimestamp())
                statement.executeUpdate()
            }
        }
    }

    /** Active user owning a valid (not revoked, not expired) token of [kind]; also stamps last_used_at. */
    fun findUserByToken(tokenHash: String, kind: TokenKind): UserRecord? = dataSource.connection.use { connection ->
        val user = connection.prepareStatement(
            """
            SELECT $userColumns
            FROM cactivity.auth_token t
            JOIN cactivity.app_user u ON u.id = t.user_id
            WHERE t.token_hash = ? AND t.kind = ? AND t.revoked_at IS NULL
              AND (t.expires_at IS NULL OR t.expires_at > SYSUTCDATETIME())
              AND u.is_active = 1
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tokenHash)
            statement.setString(2, kind.name)
            statement.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
        if (user != null) {
            connection.prepareStatement(
                "UPDATE cactivity.auth_token SET last_used_at = SYSUTCDATETIME() WHERE token_hash = ?",
            ).use { statement ->
                statement.setString(1, tokenHash)
                statement.executeUpdate()
            }
        }
        user
    }

    fun revokeToken(tokenHash: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE cactivity.auth_token SET revoked_at = SYSUTCDATETIME() WHERE token_hash = ? AND revoked_at IS NULL",
            ).use { statement ->
                statement.setString(1, tokenHash)
                statement.executeUpdate()
            }
        }
    }

    /** Signs a user out of every device (used when the password is reset). */
    fun revokeAllTokens(userId: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE cactivity.auth_token SET revoked_at = SYSUTCDATETIME() WHERE user_id = ? AND revoked_at IS NULL",
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeUpdate()
            }
        }
    }

    private fun ResultSet.toUser() = UserRecord(
        id = getLong("id"),
        username = getString("username"),
        displayName = getString("display_name"),
        userCode = getString("user_code"),
        role = UserRole.valueOf(getString("role")),
        passwordHash = getString("password_hash"),
        isActive = getBoolean("is_active"),
        oidcIssuer = getString("oidc_issuer"),
        oidcSubject = getString("oidc_subject"),
    )
}
