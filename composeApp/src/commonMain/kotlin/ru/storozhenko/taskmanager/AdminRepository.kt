package ru.storozhenko.taskmanager

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import ru.storozhenko.taskmanager.models.UpdateUserRoleRequest
import ru.storozhenko.taskmanager.models.UpdateWorkspaceRequest
import ru.storozhenko.taskmanager.models.UserProfileModel
import ru.storozhenko.taskmanager.models.WorkspaceModel

class AdminRepository(private val token: String) {
    private val client = ApiClient.http
    private val auth = ApiClient.authHeader(token)

    suspend fun getMe(): Result<UserProfileModel> = runCatching {
        client.get("$API_BASE/users/me") { auth() }.body()
    }

    suspend fun getUsers(): Result<List<UserProfileModel>> = runCatching {
        client.get("$API_BASE/admin/users") { auth() }.body()
    }

    suspend fun updateUserRole(userId: Int, role: String): Result<Unit> = runCatching {
        client.put("$API_BASE/admin/users/$userId/role") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRoleRequest(role))
        }
        Unit
    }

    suspend fun banUser(userId: Int, isBanned: Boolean): Result<Unit> = runCatching {
        client.put("$API_BASE/admin/users/$userId/ban") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("isBanned" to isBanned))
        }
        Unit
    }

    suspend fun getAllWorkspaces(): Result<List<WorkspaceModel>> = runCatching {
        client.get("$API_BASE/admin/workspaces") { auth() }.body()
    }

    suspend fun updateWorkspace(workspaceId: Int, request: UpdateWorkspaceRequest): Result<Unit> = runCatching {
        client.put("$API_BASE/admin/workspaces/$workspaceId") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        Unit
    }

    suspend fun deleteWorkspace(workspaceId: Int): Result<Unit> = runCatching {
        client.delete("$API_BASE/admin/workspaces/$workspaceId") { auth() }
        Unit
    }
}
