package ru.storozhenko.taskmanager

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

const val API_BASE = "http://localhost:8081"

object ApiClient {
    val http: HttpClient = createHttpClient()

    fun authHeader(token: String): HttpRequestBuilder.() -> Unit = {
        header("Authorization", "Bearer $token")
    }
}