package ru.storozhenko.taskmanager

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import ru.storozhenko.taskmanager.models.AuthResponse
import ru.storozhenko.taskmanager.models.LoginRequest
import ru.storozhenko.taskmanager.models.RegisterRequest

expect fun createHttpClient(): HttpClient

private const val API_BASE = "http://localhost:8081"

class AuthService {
    private val client = createHttpClient()

    suspend fun login(email: String, password: String): Result<String> = runCatching {
        val response = client.post("$API_BASE/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }
        val body = response.body<AuthResponse>()
        if (response.status == HttpStatusCode.OK) body.token
        else throw Exception(body.message)
    }

    suspend fun register(email: String, password: String): Result<String> = runCatching {
        val username = email.substringBefore("@")
        val response = client.post("$API_BASE/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username, email, password))
        }
        val body = response.body<AuthResponse>()
        if (response.status.value in 200..299) body.message
        else throw Exception(body.message)
    }
}