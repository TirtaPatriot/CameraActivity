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
            )
        }
    }
}
