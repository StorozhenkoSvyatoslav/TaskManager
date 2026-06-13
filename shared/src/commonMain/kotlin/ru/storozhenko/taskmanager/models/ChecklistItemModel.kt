package ru.storozhenko.taskmanager.models

import kotlinx.serialization.Serializable

@Serializable
data class ChecklistItemModel(
    val id: Int,
    val taskId: Int,
    val text: String,
    val isCompleted: Boolean,
    val createdAt: Long,
)

@Serializable
data class CreateChecklistItemRequest(val text: String)

@Serializable
data class UpdateChecklistItemRequest(val isCompleted: Boolean)
