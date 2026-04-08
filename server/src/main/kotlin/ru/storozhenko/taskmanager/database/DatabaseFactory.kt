package ru.storozhenko.taskmanager.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.Tasks
import ru.storozhenko.taskmanager.database.tables.Users
import ru.storozhenko.taskmanager.database.tables.Workspaces

object DatabaseFactory {
    fun init() {
        val driverClassName = "org.postgresql.Driver"
        val jdbcUrl = "jdbc:postgresql://localhost:5432/task_manager"
        val dbUser = "postgres"
        val dbPassword = "123"

        val database = Database.connect(
            url = jdbcUrl,
            driver = driverClassName,
            user = dbUser,
            password = dbPassword
        )
        transaction(database) {
            SchemaUtils.create(Users, Tasks, Workspaces)
        }
    }
}