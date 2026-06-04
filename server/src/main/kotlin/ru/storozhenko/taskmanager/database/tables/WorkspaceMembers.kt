package ru.storozhenko.taskmanager.database.tables

import org.jetbrains.exposed.sql.Table

/**
 * Членство пользователей в пространствах.
 * Роли пока минимальные:
 * - OWNER: полный доступ (создание/изменение/удаление задач)
 * - MEMBER: только чтение задач (в будущем: комментарии/файлы)
 */
object WorkspaceMembers : Table("workspace_members") {
    val workspaceId = integer("workspace_id").references(Workspaces.id)
    val userId = integer("user_id").references(Users.id)
    val role = varchar("role", 20) // OWNER | MEMBER

    override val primaryKey = PrimaryKey(workspaceId, userId)
}

