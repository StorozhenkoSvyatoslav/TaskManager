package ru.storozhenko.taskmanager.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object TaskChecklists : Table("task_checklists") {
    val id = integer("id").autoIncrement()
    val taskId = integer("task_id").references(Tasks.id)
    val text = text("text")
    val isCompleted = bool("is_completed").default(false)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}
