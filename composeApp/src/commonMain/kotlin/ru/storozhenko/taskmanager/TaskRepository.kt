package ru.storozhenko.taskmanager

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.storozhenko.taskmanager.models.CommentModel
import ru.storozhenko.taskmanager.models.CreateCommentRequest
import ru.storozhenko.taskmanager.models.CreateTaskRequest
import ru.storozhenko.taskmanager.models.TaskDetailModel
import ru.storozhenko.taskmanager.models.TaskModel
import ru.storozhenko.taskmanager.models.WorkspaceStats

class TaskRepository(private val token: String) {
    private val client = ApiClient.http
    private val auth = ApiClient.authHeader(token)

    suspend fun getTasksByWorkspace(workspaceId: Int): Result<List<TaskModel>> = runCatching {
        client.get("$API_BASE/tasks/workspace/$workspaceId") { auth() }.body()
    }

    suspend fun getWorkspaceStats(workspaceId: Int): Result<WorkspaceStats> = runCatching {
        client.get("$API_BASE/workspaces/$workspaceId/stats") { auth() }.body()
    }

    suspend fun getTask(taskId: Int): Result<TaskDetailModel> = runCatching {
        client.get("$API_BASE/tasks/$taskId") { auth() }.body()
    }

    suspend fun getComments(taskId: Int): Result<List<CommentModel>> = runCatching {
        client.get("$API_BASE/tasks/$taskId/comments") { auth() }.body()
    }

    suspend fun postComment(taskId: Int, content: String): Result<Unit> = runCatching {
        client.post("$API_BASE/tasks/$taskId/comments") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CreateCommentRequest(content))
        }
        Unit
    }

    suspend fun deleteComment(taskId: Int, commentId: Int): Result<Unit> = runCatching {
        client.delete("$API_BASE/tasks/$taskId/comments/$commentId") { auth() }
        Unit
    }

    suspend fun createTask(request: CreateTaskRequest): Result<Unit> = runCatching {
        client.post("$API_BASE/tasks") {
            auth()
            setBody(MultiPartFormDataContent(
                formData {
                    append("task", Json.encodeToString(request))
                }
            ))
        }
        Unit
    }

    suspend fun updateTask(taskId: Int, request: CreateTaskRequest): Result<Unit> = runCatching {
        client.put("$API_BASE/tasks/$taskId") {
            auth()
            setBody(MultiPartFormDataContent(
                formData {
                    append("task", Json.encodeToString(request))
                }
            ))
        }
        Unit
    }
}
