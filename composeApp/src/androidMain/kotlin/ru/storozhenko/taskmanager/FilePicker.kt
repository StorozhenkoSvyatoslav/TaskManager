package ru.storozhenko.taskmanager

// Android file picking requires Activity context — not implemented in KMP common flow
actual suspend fun pickFile(): PickedFile? = null

actual suspend fun platformUpload(url: String, token: String, file: PickedFile): Result<Unit>? = null

actual suspend fun saveFile(fileName: String, bytes: ByteArray) { /* requires MediaStore/SAF */ }
