package ru.storozhenko.taskmanager

actual fun getApiBaseUrl(): String =
    System.getenv("API_BASE_URL")
        ?: object {}::class.java.getResourceAsStream("/api_base_url.txt")
            ?.bufferedReader()?.use { it.readText().trim() }
        ?: "http://localhost:8081"
