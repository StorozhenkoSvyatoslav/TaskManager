package ru.storozhenko.taskmanager

// Android file picking requires Activity context — not implemented in KMP common flow
actual suspend fun pickFile(): PickedFile? = null
