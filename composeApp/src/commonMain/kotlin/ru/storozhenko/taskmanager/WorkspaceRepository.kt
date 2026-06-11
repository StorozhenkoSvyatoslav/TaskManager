package ru.storozhenko.taskmanager

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import ru.storozhenko.taskmanager.models.CreateWorkspaceRequest
import ru.storozhenko.taskmanager.models.InviteCodeResponse
import ru.storozhenko.taskmanager.models.UpdateWorkspaceRequest
import ru.storozhenko.taskmanager.models.WorkspaceMemberModel
import ru.storozhenko.taskmanager.models.WorkspaceModel

class WorkspaceRepository(private val token: String) {
    private val client = ApiClient.http
    private val auth = ApiClient.authHeader(token)

    suspend fun getPublicWorkspaces(): Result<List<WorkspaceModel>> = runCatching {
        client.get("$API_BASE/workspaces/public") { auth() }.body()
    }

    suspend fun getMyWorkspaces(): Result<List<WorkspaceModel>> = runCatching {
        client.get("$API_BASE/workspaces") { auth() }.body()
    }

    suspend fun createWorkspace(request: CreateWorkspaceRequest): Result<WorkspaceModel> = runCatching {
        val response = client.post("$API_BASE/workspaces") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        response.body()
    }

    suspend fun joinWorkspace(inviteCode: String): Result<WorkspaceModel> = runCatching {
        val response = client.post("$API_BASE/workspaces/join") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ru.storozhenko.taskmanager.models.JoinWorkspaceRequest(inviteCode))
        }
        response.body()
    }

    suspend fun getMembers(workspaceId: Int): Result<List<WorkspaceMemberModel>> = runCatching {
        client.get("$API_BASE/workspaces/$workspaceId/members") { auth() }.body()
    }

    suspend fun removeMember(workspaceId: Int, userId: Int): Result<Unit> = runCatching {
        client.delete("$API_BASE/workspaces/$workspaceId/members/$userId") { auth() }
        Unit
    }

    suspend fun updateWorkspace(workspaceId: Int, request: UpdateWorkspaceRequest): Result<Unit> = runCatching {
        client.put("$API_BASE/workspaces/$workspaceId") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        Unit
    }

    suspend fun generateInviteCode(workspaceId: Int, force: Boolean = false): Result<InviteCodeResponse> = runCatching {
        val url = if (force) "$API_BASE/workspaces/$workspaceId/invite?force=true"
                  else       "$API_BASE/workspaces/$workspaceId/invite"
        client.post(url) { auth() }.body()
    }
}