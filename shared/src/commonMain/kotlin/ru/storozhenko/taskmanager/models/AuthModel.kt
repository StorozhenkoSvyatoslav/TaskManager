package ru.storozhenko.taskmanager.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val message: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)