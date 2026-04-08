package ru.storozhenko.taskmanager.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object Workspaces : Table("workspaces") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val visibility = varchar("visibility", 50) // "PUBLIC" или "PRIVATE"
    val inviteCode = varchar("invite_code", 255).nullable()

    // Владелец пространства
    val ownerId = integer("owner_id").references(Users.id)

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}

