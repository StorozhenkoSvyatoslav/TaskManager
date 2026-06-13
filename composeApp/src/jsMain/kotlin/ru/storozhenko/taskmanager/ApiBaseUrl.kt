package ru.storozhenko.taskmanager

actual fun getApiBaseUrl(): String = js("window.__API_BASE__ || 'http://localhost:8081'")
