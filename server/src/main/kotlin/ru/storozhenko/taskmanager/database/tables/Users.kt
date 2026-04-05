package ru.storozhenko.taskmanager.database.tables

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50)
    val email = varchar("email", 100).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 20).default("USER")
    override val primaryKey = PrimaryKey(id)
}