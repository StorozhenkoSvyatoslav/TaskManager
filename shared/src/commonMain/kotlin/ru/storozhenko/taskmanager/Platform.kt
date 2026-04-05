package ru.storozhenko.taskmanager

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform