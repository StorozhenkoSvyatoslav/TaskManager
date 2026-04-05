package ru.storozhenko.taskmanager

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return "Pososite, ${platform.name}!"
    }
}