package ru.storozhenko.taskmanager

expect object TokenStorage {
    fun save(token: String)
    fun load(): String?
    fun clear()
}