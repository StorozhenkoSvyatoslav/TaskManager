package ru.storozhenko.taskmanager

import io.ktor.client.call.*
import io.ktor.client.request.*
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
}
