package ru.storozhenko.taskmanager.models

import kotlinx.serialization.Serializable

@Serializable
data class CommentModel(
    val id: Int,
    val taskId: Int,
    val authorId: Int,
    val authorUsername: String,
    val content: String,
    val createdAt: Long,
)

@Serializable
data class CreateCommentRequest(
    val content: String,
)