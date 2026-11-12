package id.tirtapatriot.activitycamera.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource

/** Shared SQL Server connection pool. All app objects live in the "cactivity" schema. */
class Database(config: ServerConfig) : AutoCloseable {
    val dataSource: DataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            maximumPoolSize = config.dbPoolSize
            poolName = "activity-camera"
        },
    )

    fun ping() {
        dataSource.connection.use { connection -> connection.createStatement().use { it.execute("SELECT 1") } }
    }

    override fun close() = (dataSource as HikariDataSource).close()
}

/** Runs blocking JDBC/file work off the request threads. */
suspend fun <T> blocking(block: () -> T): T = withContext(Dispatchers.IO) { block() }

internal fun PreparedStatement.setNullableDouble(index: Int, value: Double?) {
    if (value == null) setNull(index, Types.DOUBLE) else setDouble(index, value)
}

internal fun PreparedStatement.setNullableString(index: Int, value: String?) {
    if (value == null) setNull(index, Types.NVARCHAR) else setString(index, value)
}

internal fun ResultSet.getNullableDouble(column: String): Double? = getDouble(column).takeIf { !wasNull() }

/** DATETIME2 columns are written with SYSUTCDATETIME(), i.e. UTC without offset. */
internal fun ResultSet.getUtcInstant(column: String): Instant? =
    getTimestamp(column)?.toLocalDateTime()?.toInstant(ZoneOffset.UTC)

internal fun Instant.toUtcTimestamp(): Timestamp = Timestamp.valueOf(atOffset(ZoneOffset.UTC).toLocalDateTime())
