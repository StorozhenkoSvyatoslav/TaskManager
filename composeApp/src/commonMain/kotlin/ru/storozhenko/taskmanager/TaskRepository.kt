package ru.storozhenko.taskmanager

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.storozhenko.taskmanager.models.ChecklistItemModel
import ru.storozhenko.taskmanager.models.CommentModel
import ru.storozhenko.taskmanager.models.CreateChecklistItemRequest
import ru.storozhenko.taskmanager.models.CreateCommentRequest
import ru.storozhenko.taskmanager.models.CreateTaskRequest
import ru.storozhenko.taskmanager.models.TaskDetailModel
import ru.storozhenko.taskmanager.models.TaskModel
import ru.storozhenko.taskmanager.models.UpdateChecklistItemRequest
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

    suspend fun createTask(request: CreateTaskRequest, files: List<PickedFile> = emptyList()): Result<Unit> = runCatching {
        client.post("$API_BASE/tasks") {
            auth()
            setBody(MultiPartFormDataContent(formData {
                append("task", Json.encodeToString(request))
                files.forEach { f ->
                    append("file", f.bytes, Headers.build {
                        f.mimeType?.let { append(HttpHeaders.ContentType, it) }
                        append(HttpHeaders.ContentDisposition, "filename=\"${f.name}\"")
                    })
                }
            }))
        }
        Unit
    }

    suspend fun downloadAttachment(taskId: Int, attachmentId: Int): Result<ByteArray> = runCatching {
        client.get("$API_BASE/tasks/$taskId/attachments/$attachmentId/download") { auth() }.body()
    }

    suspend fun uploadAttachment(taskId: Int, file: PickedFile): Result<Unit> {
        val url = "$API_BASE/tasks/$taskId/attachments"
        // Use native browser upload on WasmJS to avoid binary interop issues
        platformUpload(url, token, file)?.let { return it }
        // Ktor-based upload for JVM/Android
        return runCatching {
            val response = client.post(url) {
                auth()
                setBody(MultiPartFormDataContent(formData {
                    append("file", file.bytes, Headers.build {
                        file.mimeType?.let { append(HttpHeaders.ContentType, it) }
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    })
                }))
            }
            if (!response.status.isSuccess()) {
                throw Exception("Upload failed: HTTP ${response.status.value} ${response.bodyAsText()}")
            }
        }
    }

    suspend fun deleteTask(taskId: Int): Result<Unit> = runCatching {
        client.delete("$API_BASE/tasks/$taskId") { auth() }
        Unit
    }

    suspend fun addChecklistItem(taskId: Int, text: String): Result<ChecklistItemModel> = runCatching {
        client.post("$API_BASE/tasks/$taskId/checklist") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CreateChecklistItemRequest(text))
        }.body()
    }

    suspend fun toggleChecklistItem(taskId: Int, itemId: Int, isCompleted: Boolean): Result<Unit> = runCatching {
        client.put("$API_BASE/tasks/$taskId/checklist/$itemId") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(UpdateChecklistItemRequest(isCompleted))
        }
        Unit
    }

    suspend fun deleteChecklistItem(taskId: Int, itemId: Int): Result<Unit> = runCatching {
        client.delete("$API_BASE/tasks/$taskId/checklist/$itemId") { auth() }
        Unit
    }

    suspend fun updateTask(taskId: Int, request: CreateTaskRequest, files: List<PickedFile> = emptyList()): Result<Unit> = runCatching {
        client.put("$API_BASE/tasks/$taskId") {
            auth()
            setBody(MultiPartFormDataContent(formData {
                append("task", Json.encodeToString(request))
                files.forEach { f ->
                    append("file", f.bytes, Headers.build {
                        f.mimeType?.let { append(HttpHeaders.ContentType, it) }
                        append(HttpHeaders.ContentDisposition, "filename=\"${f.name}\"")
                    })
                }
            }))
        }
        Unit
    }
}
