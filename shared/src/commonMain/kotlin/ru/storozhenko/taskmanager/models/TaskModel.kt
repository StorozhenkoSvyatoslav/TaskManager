package ru.storozhenko.taskmanager.models

import kotlinx.serialization.Serializable

@Serializable
data class TaskModel(
    val id: Int,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String,     // Например: "LOW", "MEDIUM", "HIGH"
    val authorId: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String?,
    val status: String = "TODO",
    val priority: String = "MEDIUM"
)