package ru.storozhenko.taskmanager.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.TaskAttachments
import ru.storozhenko.taskmanager.database.tables.TaskComments
import ru.storozhenko.taskmanager.database.tables.Tasks
import ru.storozhenko.taskmanager.database.tables.Users
import ru.storozhenko.taskmanager.database.tables.WorkspaceMembers
import ru.storozhenko.taskmanager.database.tables.Workspaces
import ru.storozhenko.taskmanager.models.UpdateUserRoleRequest
import ru.storozhenko.taskmanager.models.UpdateWorkspaceRequest
import ru.storozhenko.taskmanager.models.UserProfileModel
import ru.storozhenko.taskmanager.models.WorkspaceModel
import java.io.File
import java.time.ZoneOffset

fun Route.adminRouting() {
    route("/admin") {

        // --- Users ---

        get("/users") {
            val requesterId = call.adminUserId() ?: return@get
            val users = transaction {
                Users.selectAll().map {
                    UserProfileModel(
                        id = it[Users.id],
                        username = it[Users.username],
                        email = it[Users.email],
                        systemRole = it[Users.role],
                        isBanned = it[Users.isBanned]
                    )
                }
            }
            call.respond(HttpStatusCode.OK, users)
        }

        put("/users/{id}/role") {
            call.adminUserId() ?: return@put
            val targetId = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid user id")
            val request = call.receiveNullable<UpdateUserRoleRequest>()
            if (request == null || request.role !in listOf("USER", "ADMIN")) {
                call.respond(HttpStatusCode.BadRequest, "Invalid role, must be USER or ADMIN")
                return@put
            }
            val updated = transaction { Users.update({ Users.id eq targetId }) { it[role] = request.role } }
            if (updated == 0) call.respond(HttpStatusCode.NotFound, "User not found")
            else call.respond(HttpStatusCode.OK, "Role updated")
        }

        put("/users/{id}/ban") {
            call.adminUserId() ?: return@put
            val targetId = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid user id")
            val body = call.receiveNullable<Map<String, Boolean>>()
            val ban = body?.get("isBanned") ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing isBanned field")
            val updated = transaction { Users.update({ Users.id eq targetId }) { it[isBanned] = ban } }
            if (updated == 0) call.respond(HttpStatusCode.NotFound, "User not found")
            else call.respond(HttpStatusCode.OK, if (ban) "User banned" else "User unbanned")
        }

        // --- Workspaces ---

        get("/workspaces") {
            call.adminUserId() ?: return@get
            val list = transaction {
                Workspaces.selectAll().map {
                    WorkspaceModel(
                        id = it[Workspaces.id],
                        name = it[Workspaces.name],
                        description = it[Workspaces.description],
                        visibility = it[Workspaces.visibility],
                        inviteCode = it[Workspaces.inviteCode],
                        ownerId = it[Workspaces.ownerId],
                        createdAt = it[Workspaces.createdAt].toEpochSecond(ZoneOffset.UTC)
                    )
                }
            }
            call.respond(HttpStatusCode.OK, list)
        }

        put("/workspaces/{id}") {
            call.adminUserId() ?: return@put
            val workspaceId = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid workspace id")
            val request = call.receiveNullable<UpdateWorkspaceRequest>()
            if (request == null || request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                return@put
            }
            val updated = transaction {
                Workspaces.update({ Workspaces.id eq workspaceId }) {
                    it[name] = request.name
                    it[description] = request.description
                }
            }
            if (updated == 0) call.respond(HttpStatusCode.NotFound, "Workspace not found")
            else call.respond(HttpStatusCode.OK, "Workspace updated")
        }

        delete("/workspaces/{id}") {
            call.adminUserId() ?: return@delete
            val workspaceId = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid workspace id")

            val exists = transaction { Workspaces.selectAll().where { Workspaces.id eq workspaceId }.any() }
            if (!exists) {
                call.respond(HttpStatusCode.NotFound, "Workspace not found")
                return@delete
            }

            val filesToDelete = transaction {
                val taskIds = Tasks.selectAll()
                    .where { Tasks.workspaceId eq workspaceId }
                    .map { it[Tasks.id] }

                val paths = if (taskIds.isNotEmpty()) {
                    TaskAttachments.selectAll()
                        .where { TaskAttachments.taskId inList taskIds }
                        .map { it[TaskAttachments.storedPath] }
                } else emptyList()

                if (taskIds.isNotEmpty()) {
                    TaskAttachments.deleteWhere { TaskAttachments.taskId inList taskIds }
                    TaskComments.deleteWhere { TaskComments.taskId inList taskIds }
                }
                Tasks.deleteWhere { Tasks.workspaceId eq workspaceId }
                WorkspaceMembers.deleteWhere { WorkspaceMembers.workspaceId eq workspaceId }
                Workspaces.deleteWhere { Workspaces.id eq workspaceId }
                paths
            }
            filesToDelete.forEach { File(it).delete() }

            call.respond(HttpStatusCode.OK, "Workspace deleted")
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.adminUserId(): Int? {
    val userId = principal<JWTPrincipal>()?.payload?.getClaim("id")?.asInt()
    if (userId == null) {
        respond(HttpStatusCode.Unauthorized, "Invalid user")
        return null
    }
    val isAdmin = transaction { Users.select { Users.id eq userId }.singleOrNull()?.get(Users.role) == "ADMIN" }
    if (!isAdmin) {
        respond(HttpStatusCode.Forbidden, "Admin access required")
        return null
    }
    return userId
}
