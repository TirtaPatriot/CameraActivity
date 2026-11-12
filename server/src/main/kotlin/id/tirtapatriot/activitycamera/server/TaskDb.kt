package id.tirtapatriot.activitycamera.server

import id.tirtapatriot.activitycamera.model.TaskType
import javax.sql.DataSource

data class TaskRecord(
    val id: Long,
    val name: String,
    val icon: String,
    val sortOrder: Int,
    val isActive: Boolean,
) {
    fun toTaskType() = TaskType(id, name, icon)
}

/** Task types officers can pick (cactivity.task_type). */
class TaskDb(private val dataSource: DataSource) {

    fun listAll(): List<TaskRecord> = query("SELECT id, name, icon, sort_order, is_active FROM cactivity.task_type ORDER BY sort_order, name")

    fun listActive(): List<TaskRecord> =
        query("SELECT id, name, icon, sort_order, is_active FROM cactivity.task_type WHERE is_active = 1 ORDER BY sort_order, name")

    fun findActive(id: Long): TaskRecord? = listActive().firstOrNull { it.id == id }

    fun create(name: String, icon: String, sortOrder: Int): Long = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "INSERT INTO cactivity.task_type (name, icon, sort_order) OUTPUT INSERTED.id VALUES (?, ?, ?)",
        ).use { statement ->
            statement.setString(1, name)
            statement.setString(2, icon)
            statement.setInt(3, sortOrder)
            statement.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    fun update(id: Long, name: String, icon: String, sortOrder: Int) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE cactivity.task_type SET name = ?, icon = ?, sort_order = ? WHERE id = ?",
            ).use { statement ->
                statement.setString(1, name)
                statement.setString(2, icon)
                statement.setInt(3, sortOrder)
                statement.setLong(4, id)
                statement.executeUpdate()
            }
        }
    }

    fun setActive(id: Long, active: Boolean) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE cactivity.task_type SET is_active = ? WHERE id = ?").use { statement ->
                statement.setBoolean(1, active)
                statement.setLong(2, id)
                statement.executeUpdate()
            }
        }
    }

    private fun query(sql: String): List<TaskRecord> = dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            TaskRecord(
                                id = rs.getLong("id"),
                                name = rs.getString("name"),
                                icon = rs.getString("icon"),
                                sortOrder = rs.getInt("sort_order"),
                                isActive = rs.getBoolean("is_active"),
                            ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        val ICONS = listOf("meter", "inspect", "replace", "leak", "seal", "other")
    }
}
