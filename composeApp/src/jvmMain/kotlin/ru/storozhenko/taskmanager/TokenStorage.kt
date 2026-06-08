package ru.storozhenko.taskmanager

import java.util.prefs.Preferences

actual object TokenStorage {
    private val prefs = Preferences.userRoot().node("taskmanager")

    actual fun save(token: String) = prefs.put("token", token)
    actual fun load(): String? = prefs.get("token", null)
    actual fun clear() = prefs.remove("token")
}