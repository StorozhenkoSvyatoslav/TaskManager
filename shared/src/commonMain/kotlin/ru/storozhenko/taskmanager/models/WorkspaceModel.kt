package ru.storozhenko.taskmanager.models

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceModel(
    val id: Int,
    val name: String,
    val description: String?,
    val visibility: String, // "PUBLIC" или "PRIVATE"
    val inviteCode: String?,
    val ownerId: Int,
    val createdAt: Long
)

@Serializable
data class CreateWorkspaceRequest(
    val name: String,
    val description: String?,
    val visibility: String = "PUBLIC",
    val inviteCode: String? = null // если visibility == "PRIVATE"
)
