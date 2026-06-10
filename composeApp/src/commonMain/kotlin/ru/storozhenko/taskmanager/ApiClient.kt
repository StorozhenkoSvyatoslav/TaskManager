package ru.storozhenko.taskmanager

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*

const val API_BASE = "http://localhost:8081"

object ApiClient {
    var onUnauthorized: (() -> Unit)? = null

    val http: HttpClient = createHttpClient().config {
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status == HttpStatusCode.Unauthorized) {
                    onUnauthorized?.invoke()
                }
            }
        }
    }

    fun authHeader(token: String): HttpRequestBuilder.() -> Unit = {
        header("Authorization", "Bearer $token")
    }
}