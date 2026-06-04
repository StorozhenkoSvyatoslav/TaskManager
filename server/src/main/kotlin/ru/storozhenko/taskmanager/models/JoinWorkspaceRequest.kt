package ru.storozhenko.taskmanager.models

import kotlinx.serialization.Serializable

@Serializable
data class JoinWorkspaceRequest(
    val inviteCode: String
)

