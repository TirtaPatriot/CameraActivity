package id.tirtapatriot.activitycamera.server

/** Configuration from environment variables (see server/.env.example). */
data class ServerConfig(
    val port: Int,
    val apiKey: String,
    /** Asustor NAS share mounted on the server host, e.g. /mnt/nas/activity-camera */
    val storageRoot: String,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val dbPoolSize: Int,
    val maxUploadBytes: Long,
) {
    companion object {
        fun fromEnv(): ServerConfig {
            fun required(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: error("Environment variable $name is required")
            fun optional(name: String, default: String) = System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

            return ServerConfig(
                port = optional("PORT", "8080").toInt(),
                apiKey = required("API_KEY"),
                storageRoot = required("STORAGE_ROOT"),
                dbUrl = required("DB_URL"),
                dbUser = required("DB_USER"),
                dbPassword = required("DB_PASSWORD"),
                dbPoolSize = optional("DB_POOL_SIZE", "5").toInt(),
                maxUploadBytes = optional("MAX_UPLOAD_MB", "1024").toLong() * 1024 * 1024,
            )
        }
    }
}
