package ru.storozhenko.taskmanager.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.TaskAttachments
import ru.storozhenko.taskmanager.database.tables.TaskChecklists
import ru.storozhenko.taskmanager.database.tables.TaskComments
import ru.storozhenko.taskmanager.database.tables.Tasks
import ru.storozhenko.taskmanager.database.tables.Users
import ru.storozhenko.taskmanager.database.tables.WorkspaceMembers
import ru.storozhenko.taskmanager.database.tables.Workspaces

object DatabaseFactory {
    fun init() {
        val driverClassName = "org.postgresql.Driver"

        val rawUrl = System.getenv("DATABASE_URL")
        val (jdbcUrl, dbUser, dbPassword) = if (rawUrl != null) {
            parsePostgresUrl(rawUrl)
        } else {
            Triple("jdbc:postgresql://localhost:5432/task_manager", "postgres", "123")
        }

        val database = Database.connect(
            url = jdbcUrl,
            driver = driverClassName,
            user = dbUser,
            password = dbPassword
        )
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(Users, Tasks, Workspaces, WorkspaceMembers, TaskComments, TaskAttachments, TaskChecklists)
        }
    }

    // Parses Railway DATABASE_URL: postgresql://user:password@host:port/dbname
    private fun parsePostgresUrl(url: String): Triple<String, String, String> {
        val uri = java.net.URI(url.replace("postgres://", "postgresql://"))
        val userInfo = uri.userInfo?.split(":", limit = 2) ?: listOf("postgres", "")
        val port = if (uri.port == -1) 5432 else uri.port
        val jdbcUrl = "jdbc:postgresql://${uri.host}:$port${uri.path}"
        return Triple(jdbcUrl, userInfo[0], userInfo.getOrElse(1) { "" })
    }
}