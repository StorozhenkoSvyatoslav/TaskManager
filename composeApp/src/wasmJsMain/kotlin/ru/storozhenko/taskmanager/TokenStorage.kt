package ru.storozhenko.taskmanager

import kotlinx.browser.localStorage

actual object TokenStorage {
    actual fun save(token: String) = localStorage.setItem("taskmanager_token", token)
    actual fun load(): String? = localStorage.getItem("taskmanager_token")
    actual fun clear() = localStorage.removeItem("taskmanager_token")
}