package ru.storozhenko.taskmanager

data class PickedFile(val name: String, val bytes: ByteArray, val mimeType: String?)

expect suspend fun pickFile(): PickedFile?

expect suspend fun platformUpload(url: String, token: String, file: PickedFile): Result<Unit>?
