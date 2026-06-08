package ru.storozhenko.taskmanager

import android.content.Context
import android.content.SharedPreferences

actual object TokenStorage {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("taskmanager", Context.MODE_PRIVATE)
    }

    actual fun save(token: String) = prefs.edit().putString("token", token).apply()
    actual fun load(): String? = prefs.getString("token", null)
    actual fun clear() = prefs.edit().remove("token").apply()
}