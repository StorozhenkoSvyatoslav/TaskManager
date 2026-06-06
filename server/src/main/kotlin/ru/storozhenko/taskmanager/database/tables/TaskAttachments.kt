package ru.storozhenko.taskmanager.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object TaskAttachments : Table("task_attachments") {
    val id = integer("id").autoIncrement()
    val taskId = integer("task_id").references(Tasks.id)
    val uploadedBy = integer("uploaded_by").references(Users.id)
    val fileName = varchar("file_name", 255)
    val storedPath = varchar("stored_path", 500)
    val mimeType = varchar("mime_type", 100).nullable()
    val fileSize = long("file_size")
    val uploadedAt = datetime("uploaded_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}