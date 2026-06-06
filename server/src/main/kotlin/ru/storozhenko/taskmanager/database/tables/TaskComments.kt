package ru.storozhenko.taskmanager.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object TaskComments : Table("task_comments") {
    val id = integer("id").autoIncrement()
    val taskId = integer("task_id").references(Tasks.id)
    val authorId = integer("author_id").references(Users.id)
    val content = text("content")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}