package ru.storozhenko.taskmanager

import kotlin.js.JsString

@JsFun("() => (window.__API_BASE__ || 'http://localhost:8081')")
private external fun jsGetApiBaseUrl(): JsString

actual fun getApiBaseUrl(): String = jsGetApiBaseUrl().toString()
