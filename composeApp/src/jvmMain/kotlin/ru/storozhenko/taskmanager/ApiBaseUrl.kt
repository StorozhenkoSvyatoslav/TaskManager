package ru.storozhenko.taskmanager

actual fun getApiBaseUrl(): String = System.getenv("API_BASE_URL") ?: "http://localhost:8081"
