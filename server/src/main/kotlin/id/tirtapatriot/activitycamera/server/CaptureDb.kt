package id.tirtapatriot.activitycamera.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import id.tirtapatriot.activitycamera.model.CaptureMetadata
import id.tirtapatriot.activitycamera.model.CaptureSummary
import id.tirtapatriot.activitycamera.model.MediaType
import id.tirtapatriot.activitycamera.model.UploadResponse
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Capture metadata in SQL Server (table cactivity.capture, see server/sql/001_init.sql). */
class CaptureDb(config: ServerConfig) : AutoCloseable {
    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            maximumPoolSize = config.dbPoolSize
            poolName = "activity-camera"
        },
    )

    /** Date filters in the list endpoint are interpreted in WIB. */
    private val reportOffset = ZoneOffset.ofHours(7)

    fun ping() {
        dataSource.connection.use { connection -> connection.createStatement().use { it.execute("SELECT 1") } }
    }

    fun findByClientId(clientId: String): UploadResponse? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT id, storage_path FROM cactivity.capture WHERE client_id = ?").use { statement ->
            statement.setString(1, clientId)
            statement.executeQuery().use { rs ->
                if (rs.next()) UploadResponse(rs.getLong(1), clientId, rs.getString(2), duplicate = true) else null
            }
        }
    }

    fun insert(metadata: CaptureMetadata, storagePath: String, fileSize: Long, originalName: String?): Long =
        dataSource.connection.use { connection ->
            val sql = """
                INSERT INTO cactivity.capture
                  (client_id, media_type, taken_at, timezone, latitude, longitude, accuracy_m, altitude_m,
                   note, user_code, device_model, platform, storage_path, file_size, original_name)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            connection.prepareStatement(sql).use { statement ->
                val zone = runCatching { ZoneId.of(metadata.timezone) }.getOrDefault(ZoneOffset.UTC)
                statement.setString(1, metadata.clientId)
                statement.setString(2, metadata.mediaType.name)
                statement.setObject(3, Instant.ofEpochMilli(metadata.takenAtEpochMs).atZone(zone).toOffsetDateTime())
                statement.setString(4, metadata.timezone)
                statement.setNullableDouble(5, metadata.latitude)
                statement.setNullableDouble(6, metadata.longitude)
                statement.setNullableDouble(7, metadata.accuracyMeters?.toDouble())
                statement.setNullableDouble(8, metadata.altitudeMeters)
                statement.setString(9, metadata.note)
                statement.setString(10, metadata.userCode)
                statement.setString(11, metadata.deviceModel)
                statement.setString(12, metadata.platform)
                statement.setString(13, storagePath)
                statement.setLong(14, fileSize)
                statement.setString(15, originalName)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

    fun list(from: LocalDate?, to: LocalDate?, userCode: String?, limit: Int): List<CaptureSummary> =
        dataSource.connection.use { connection ->
            val conditions = mutableListOf<String>()
            val params = mutableListOf<Any>()
            from?.let {
                conditions += "taken_at >= ?"
                params += it.atStartOfDay().atOffset(reportOffset)
            }
            to?.let {
                conditions += "taken_at < ?"
                params += it.plusDays(1).atStartOfDay().atOffset(reportOffset)
            }
            userCode?.let {
                conditions += "user_code = ?"
                params += it
            }

            val sql = buildString {
                append("SELECT TOP (").append(limit.coerceIn(1, 1000)).append(") ")
                append("id, client_id, media_type, taken_at, latitude, longitude, accuracy_m, note, user_code, ")
                append("storage_path, file_size, uploaded_at FROM cactivity.capture")
                if (conditions.isNotEmpty()) append(" WHERE ").append(conditions.joinToString(" AND "))
                append(" ORDER BY taken_at DESC")
            }
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.toCaptureSummary()) }
                }
            }
        }

    fun storagePathOf(id: Long): String? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT storage_path FROM cactivity.capture WHERE id = ?").use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    override fun close() = dataSource.close()

    private fun ResultSet.toCaptureSummary() = CaptureSummary(
        id = getLong("id"),
        clientId = getString("client_id"),
        mediaType = MediaType.valueOf(getString("media_type")),
        takenAtEpochMs = getObject("taken_at", OffsetDateTime::class.java).toInstant().toEpochMilli(),
        latitude = getNullableDouble("latitude"),
        longitude = getNullableDouble("longitude"),
        accuracyMeters = getNullableDouble("accuracy_m")?.toFloat(),
        note = getString("note"),
        userCode = getString("user_code"),
        storagePath = getString("storage_path"),
        fileSize = getLong("file_size"),
        // uploaded_at is stored in UTC (SYSUTCDATETIME)
        uploadedAtEpochMs = getTimestamp("uploaded_at").toLocalDateTime().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )

    private fun ResultSet.getNullableDouble(column: String): Double? = getDouble(column).takeIf { !wasNull() }

    private fun PreparedStatement.setNullableDouble(index: Int, value: Double?) {
        if (value == null) setNull(index, Types.DOUBLE) else setDouble(index, value)
    }
}
