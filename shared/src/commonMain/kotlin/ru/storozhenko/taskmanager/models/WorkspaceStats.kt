package ru.storozhenko.taskmanager.models

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceStats(
    val workspaceId: Int,
    val totalTasks: Int,
    val tasksByStatus: Map<String, Int>,
    val tasksByPriority: Map<String, Int>,
    val totalComments: Int,
    val totalMembers: Int,
    val totalAttachments: Int,
)