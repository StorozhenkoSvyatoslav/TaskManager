package ru.storozhenko.taskmanager

data class PickedFile(val name: String, val bytes: ByteArray, val mimeType: String?)

expect suspend fun pickFile(): PickedFile?
