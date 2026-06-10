package ru.storozhenko.taskmanager.models

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceMemberModel(
    val userId: Int,
    val username: String,
    val role: String, // "OWNER" | "MEMBER"
)
