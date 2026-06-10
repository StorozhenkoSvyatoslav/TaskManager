package ru.storozhenko.taskmanager.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileModel(
    val id: Int,
    val username: String,
    val email: String,
    val systemRole: String,
    val isBanned: Boolean = false
)

@Serializable
data class UpdateUserRoleRequest(
    val role: String
)
