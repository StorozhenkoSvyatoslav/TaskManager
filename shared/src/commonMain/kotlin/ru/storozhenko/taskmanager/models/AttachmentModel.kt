package ru.storozhenko.taskmanager.models

import kotlinx.serialization.Serializable

@Serializable
data class AttachmentModel(
    val id: Int,
    val taskId: Int,
    val uploadedBy: Int,
    val fileName: String,
    val mimeType: String?,
    val fileSize: Long,
    val uploadedAt: Long,
)