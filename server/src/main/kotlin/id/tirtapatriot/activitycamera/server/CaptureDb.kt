package id.tirtapatriot.activitycamera.server

import id.tirtapatriot.activitycamera.model.CaptureMetadata
import id.tirtapatriot.activitycamera.model.CaptureSummary
import id.tirtapatriot.activitycamera.model.MediaType
import id.tirtapatriot.activitycamera.model.UploadResponse
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.sql.DataSource

/** Capture metadata in SQL Server (table cactivity.capture). */
class CaptureDb(private val dataSource: DataSource, private val reportZone: ZoneId) {

    fun findByClientId(clientId: String): UploadResponse? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT id, storage_path FROM cactivity.capture WHERE client_id = ?").use { statement ->
            statement.setString(1, clientId)
            statement.executeQuery().use { rs ->
                if (rs.next()) UploadResponse(rs.getLong(1), clientId, rs.getString(2), duplicate = true) else null
            }
        }
    }

    fun insert(
        metadata: CaptureMetadata,
        userId: Long?,
        taskTypeId: Long?,
        storagePath: String,
        fileSize: Long,
        originalName: String?,
    ): Long = dataSource.connection.use { connection ->
        val sql = """
            INSERT INTO cactivity.capture
              (client_id, media_type, taken_at, timezone, latitude, longitude, accuracy_m, altitude_m,
               note, user_code, device_model, platform, storage_path, file_size, original_name, user_id,
               task_type_id, task_name, is_mock_location, device_id, shared_at)
            OUTPUT INSERTED.id
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            statement.setNullableString(9, metadata.note)
            statement.setNullableString(10, metadata.userCode)
            statement.setNullableString(11, metadata.deviceModel)
            statement.setNullableString(12, metadata.platform)
            statement.setString(13, storagePath)
            statement.setLong(14, fileSize)
            statement.setNullableString(15, originalName)
            if (userId == null) statement.setNull(16, java.sql.Types.BIGINT) else statement.setLong(16, userId)
            if (taskTypeId == null) statement.setNull(17, java.sql.Types.BIGINT) else statement.setLong(17, taskTypeId)
            statement.setNullableString(18, metadata.taskName?.take(100))
            statement.setBoolean(19, metadata.isMockLocation)
            statement.setNullableString(20, metadata.deviceId?.take(64))
            val sharedAt = metadata.sharedAtEpochMs?.let { Instant.ofEpochMilli(it).atZone(zone).toOffsetDateTime() }
            if (sharedAt == null) statement.setNull(21, java.sql.Types.TIMESTAMP_WITH_TIMEZONE) else statement.setObject(21, sharedAt)
            statement.executeQuery().use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }

    /** Latest captures, optionally filtered by date range (in [reportZone]) and user. */
    fun list(from: LocalDate?, to: LocalDate?, userId: Long?, limit: Int): List<CaptureSummary> =
        dataSource.connection.use { connection ->
            val conditions = mutableListOf<String>()
            val params = mutableListOf<Any>()
            from?.let {
                conditions += "c.taken_at >= ?"
                params += it.atStartOfDay(reportZone).toOffsetDateTime()
            }
            to?.let {
                conditions += "c.taken_at < ?"
                params += it.plusDays(1).atStartOfDay(reportZone).toOffsetDateTime()
            }
            userId?.let {
                conditions += "c.user_id = ?"
                params += it
            }

            val sql = buildString {
                append("SELECT TOP (").append(limit.coerceIn(1, 1000)).append(") ")
                append("c.id, c.client_id, c.media_type, c.taken_at, c.latitude, c.longitude, c.accuracy_m, c.note, ")
                append("c.user_code, u.display_name, c.storage_path, c.file_size, c.uploaded_at, ")
                append("c.task_name, c.is_mock_location, c.device_id, c.device_model, c.shared_at ")
                append("FROM cactivity.capture c LEFT JOIN cactivity.app_user u ON u.id = c.user_id")
                if (conditions.isNotEmpty()) append(" WHERE ").append(conditions.joinToString(" AND "))
                append(" ORDER BY c.taken_at DESC")
            }
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.toCaptureSummary()) }
                }
            }
        }

    /** Records the first time a capture was shared; true when the capture exists. */
    fun markShared(clientId: String, sharedAt: Instant, zone: ZoneId): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE cactivity.capture SET shared_at = COALESCE(shared_at, ?) WHERE client_id = ?",
        ).use { statement ->
            statement.setObject(1, sharedAt.atZone(zone).toOffsetDateTime())
            statement.setString(2, clientId)
            statement.executeUpdate() > 0
        }
    }

    /** Replaces the note; true when the capture exists. */
    fun updateNote(clientId: String, note: String?): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE cactivity.capture SET note = ? WHERE client_id = ?").use { statement ->
            statement.setNullableString(1, note)
            statement.setString(2, clientId)
            statement.executeUpdate() > 0
        }
    }

    fun storagePathOf(id: Long): String? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT storage_path FROM cactivity.capture WHERE id = ?").use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    private fun ResultSet.toCaptureSummary() = CaptureSummary(
        id = getLong("id"),
        clientId = getString("client_id"),
        mediaType = MediaType.valueOf(getString("media_type")),
        takenAtEpochMs = getObject("taken_at", OffsetDateTime::class.java).toInstant().toEpochMilli(),
        latitude = getNullableDouble("latitude"),
        longitude = getNullableDouble("longitude"),
        accuracyMeters = getNullableDouble("accuracy_m")?.toFloat(),
        isMockLocation = getBoolean("is_mock_location"),
        note = getString("note"),
        taskName = getString("task_name"),
        userCode = getString("user_code"),
        userDisplayName = getString("display_name"),
        deviceId = getString("device_id"),
        deviceModel = getString("device_model"),
        storagePath = getString("storage_path"),
        fileSize = getLong("file_size"),
        sharedAtEpochMs = getObject("shared_at", OffsetDateTime::class.java)?.toInstant()?.toEpochMilli(),
        uploadedAtEpochMs = getUtcInstant("uploaded_at")?.toEpochMilli() ?: 0L,
    )
}
