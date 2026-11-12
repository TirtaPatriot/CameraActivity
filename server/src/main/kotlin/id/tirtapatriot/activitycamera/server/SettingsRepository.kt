package id.tirtapatriot.activitycamera.server

import id.tirtapatriot.activitycamera.model.AppConfig
import id.tirtapatriot.activitycamera.model.Branding
import id.tirtapatriot.activitycamera.model.CapturePolicy
import kotlinx.serialization.json.Json
import javax.sql.DataSource

/**
 * Server-managed settings in cactivity.setting (key → JSON/text), cached in memory.
 * Edited from the admin panel and served to the apps via GET /api/v1/config.
 */
class SettingsRepository(private val dataSource: DataSource, private val tasks: TaskDb) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var cachedConfig: AppConfig? = null

    fun appConfig(): AppConfig = cachedConfig ?: loadConfig().also { cachedConfig = it }

    /** Call after task types change so /api/v1/config serves the new list. */
    fun invalidate() {
        cachedConfig = null
    }

    fun saveBranding(branding: Branding) {
        put(KEY_BRANDING, json.encodeToString(Branding.serializer(), branding))
        cachedConfig = null
    }

    fun saveCapturePolicy(policy: CapturePolicy) {
        put(KEY_CAPTURE, json.encodeToString(CapturePolicy.serializer(), policy))
        cachedConfig = null
    }

    fun logoContentType(): String? = get(KEY_LOGO_CONTENT_TYPE)

    fun setLogo(contentType: String?) {
        if (contentType == null) delete(KEY_LOGO_CONTENT_TYPE) else put(KEY_LOGO_CONTENT_TYPE, contentType)
        val current = appConfig().branding
        saveBranding(current.copy(logoVersion = if (contentType == null) null else System.currentTimeMillis()))
    }

    private fun loadConfig(): AppConfig {
        val branding = get(KEY_BRANDING)
            ?.let { runCatching { json.decodeFromString(Branding.serializer(), it) }.getOrNull() }
            ?: Branding()
        val capture = get(KEY_CAPTURE)
            ?.let { runCatching { json.decodeFromString(CapturePolicy.serializer(), it) }.getOrNull() }
            ?: CapturePolicy()
        return AppConfig(branding, capture, tasks.listActive().map { it.toTaskType() })
    }

    private fun get(key: String): String? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT value FROM cactivity.setting WHERE setting_key = ?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    private fun put(key: String, value: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                MERGE cactivity.setting AS target
                USING (SELECT ? AS setting_key, ? AS value) AS source
                ON target.setting_key = source.setting_key
                WHEN MATCHED THEN UPDATE SET value = source.value, updated_at = SYSUTCDATETIME()
                WHEN NOT MATCHED THEN INSERT (setting_key, value) VALUES (source.setting_key, source.value);
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, key)
                statement.setString(2, value)
                statement.executeUpdate()
            }
        }
    }

    private fun delete(key: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM cactivity.setting WHERE setting_key = ?").use { statement ->
                statement.setString(1, key)
                statement.executeUpdate()
            }
        }
    }

    private companion object {
        const val KEY_BRANDING = "branding"
        const val KEY_CAPTURE = "capture_policy"
        const val KEY_LOGO_CONTENT_TYPE = "logo_content_type"
    }
}
