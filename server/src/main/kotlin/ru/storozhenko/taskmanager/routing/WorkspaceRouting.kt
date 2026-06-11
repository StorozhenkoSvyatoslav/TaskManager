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
import ru.storozhenko.taskmanager.models.CreateWorkspaceRequest
import ru.storozhenko.taskmanager.models.InviteCodeResponse
import ru.storozhenko.taskmanager.models.JoinWorkspaceRequest
import ru.storozhenko.taskmanager.models.UpdateWorkspaceRequest
import ru.storozhenko.taskmanager.models.WorkspaceMemberModel
import ru.storozhenko.taskmanager.models.WorkspaceModel
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

fun Route.workspaceRouting() {
    route("/workspaces") {

        // Получить список всех ПУБЛИЧНЫХ пространств
        get("/public") {
            val publicWorkspaces = transaction {
                Workspaces.selectAll().where { Workspaces.visibility eq "PUBLIC" }.map {
                    WorkspaceModel(
                        id = it[Workspaces.id],
                        name = it[Workspaces.name],
                        description = it[Workspaces.description],
                        visibility = it[Workspaces.visibility],
                        inviteCode = null, // Скрываем инвайт-код в целях безопасности
                        ownerId = it[Workspaces.ownerId],
                        createdAt = it[Workspaces.createdAt].toEpochSecond(ZoneOffset.UTC)
                    )
                }
            }
            call.respond(HttpStatusCode.OK, publicWorkspaces)
        }

        // Получить список пространств текущего пользователя
        get {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@get
            }

            val workspaces = transaction {
                (Workspaces innerJoin WorkspaceMembers)
                    .selectAll()
                    .where { WorkspaceMembers.userId eq userId }
                    .map {
                        WorkspaceModel(
                            id = it[Workspaces.id],
                            name = it[Workspaces.name],
                            description = it[Workspaces.description],
                            visibility = it[Workspaces.visibility],
                            inviteCode = if (it[Workspaces.ownerId] == userId) it[Workspaces.inviteCode] else null,
                            ownerId = it[Workspaces.ownerId],
                            createdAt = it[Workspaces.createdAt].toEpochSecond(ZoneOffset.UTC)
                        )
                    }
            }
            call.respond(HttpStatusCode.OK, workspaces)
        }

        // Создать новое пространство
        post {
            val request = call.receiveNullable<CreateWorkspaceRequest>()
            if (request == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                return@post
            }

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@post
            }

            val created = transaction {
                val id = Workspaces.insert {
                    it[name] = request.name
                    it[description] = request.description
                    it[visibility] = request.visibility
                    it[inviteCode] = request.inviteCode
                    it[ownerId] = userId
                } get Workspaces.id

                WorkspaceMembers.insertIgnore {
                    it[workspaceId] = id
                    it[WorkspaceMembers.userId] = userId
                    it[role] = "OWNER"
                }

                Workspaces.selectAll().where { Workspaces.id eq id }.single()
            }

            call.respond(
                HttpStatusCode.Created,
                WorkspaceModel(
                    id = created[Workspaces.id],
                    name = created[Workspaces.name],
                    description = created[Workspaces.description],
                    visibility = created[Workspaces.visibility],
                    inviteCode = created[Workspaces.inviteCode],
                    ownerId = created[Workspaces.ownerId],
                    createdAt = created[Workspaces.createdAt].toEpochSecond(ZoneOffset.UTC)
                )
            )
        }

        // Получить участников пространства (только члены)
        get("/{id}/members") {
            val workspaceId = call.parameters["id"]?.toIntOrNull()
            if (workspaceId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid workspace id")
                return@get
            }
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@get
            }
            val isMember = transaction {
                WorkspaceMembers.selectAll()
                    .where {
                        (WorkspaceMembers.workspaceId eq workspaceId) and
                        (WorkspaceMembers.userId eq userId)
                    }
                    .any()
            }
            if (!isMember) {
                call.respond(HttpStatusCode.Forbidden, "Not a member")
                return@get
            }
            val members = transaction {
                (WorkspaceMembers innerJoin Users)
                    .select(WorkspaceMembers.userId, Users.username, WorkspaceMembers.role)
                    .where { WorkspaceMembers.workspaceId eq workspaceId }
                    .map {
                        WorkspaceMemberModel(
                            userId   = it[WorkspaceMembers.userId],
                            username = it[Users.username],
                            role     = it[WorkspaceMembers.role],
                        )
                    }
            }
            call.respond(HttpStatusCode.OK, members)
        }

        // Получить/сгенерировать invite code (только OWNER приватного workspace)
        // Возвращает действующий код если он ещё не истёк, иначе генерирует новый.
        // Параметр ?force=true принудительно генерирует новый код.
        post("/{id}/invite") {
            val workspaceId = call.parameters["id"]?.toIntOrNull()
            if (workspaceId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid workspace id")
                return@post
            }
            val force = call.request.queryParameters["force"] == "true"

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@post
            }

            val now = LocalDateTime.now(ZoneOffset.UTC)

            val result = transaction {
                val row = Workspaces
                    .select(Workspaces.ownerId, Workspaces.visibility, Workspaces.inviteCode, Workspaces.inviteCodeExpiresAt)
                    .where { Workspaces.id eq workspaceId }
                    .limit(1)
                    .firstOrNull() ?: return@transaction null

                if (row[Workspaces.ownerId] != userId) return@transaction null

                val existingCode    = row[Workspaces.inviteCode]
                val existingExpiry  = row[Workspaces.inviteCodeExpiresAt]
                val isStillValid    = !force && existingCode != null && existingExpiry != null && existingExpiry.isAfter(now)

                if (isStillValid && existingCode != null && existingExpiry != null) {
                    return@transaction InviteCodeResponse(
                        code      = existingCode,
                        expiresAt = existingExpiry.toEpochSecond(ZoneOffset.UTC)
                    )
                }

                val newCode    = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
                val newExpiry  = now.plusHours(1)

                Workspaces.update({ Workspaces.id eq workspaceId }) {
                    it[inviteCode]          = newCode
                    it[inviteCodeExpiresAt] = newExpiry
                }

                InviteCodeResponse(
                    code      = newCode,
                    expiresAt = newExpiry.toEpochSecond(ZoneOffset.UTC)
                )
            }

            if (result == null) {
                call.respond(HttpStatusCode.Forbidden, "Only workspace owner can generate invite codes")
                return@post
            }

            call.respond(HttpStatusCode.OK, result)
        }

        // Удалить участника из пространства (только OWNER; нельзя удалить самого себя)
        delete("/{id}/members/{userId}") {
            val workspaceId = call.parameters["id"]?.toIntOrNull()
            val targetUserId = call.parameters["userId"]?.toIntOrNull()
            if (workspaceId == null || targetUserId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid parameters")
                return@delete
            }
            val principal = call.principal<JWTPrincipal>()
            val requesterId = principal?.payload?.getClaim("id")?.asInt()
            if (requesterId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@delete
            }
            val isOwner = transaction {
                WorkspaceMembers.selectAll()
                    .where {
                        (WorkspaceMembers.workspaceId eq workspaceId) and
                        (WorkspaceMembers.userId eq requesterId) and
                        (WorkspaceMembers.role eq "OWNER")
                    }
                    .any()
            }
            if (!isOwner) {
                call.respond(HttpStatusCode.Forbidden, "Only workspace owner can remove members")
                return@delete
            }
            if (requesterId == targetUserId) {
                call.respond(HttpStatusCode.BadRequest, "Owner cannot remove themselves")
                return@delete
            }
            val deleted = transaction {
                WorkspaceMembers.deleteWhere {
                    (WorkspaceMembers.workspaceId eq workspaceId) and
                    (WorkspaceMembers.userId eq targetUserId)
                }
            }
            if (deleted == 0) {
                call.respond(HttpStatusCode.NotFound, "Member not found")
            } else {
                call.respond(HttpStatusCode.OK, "Member removed")
            }
        }

        // Обновить название/описание пространства (только OWNER)
        put("/{id}") {
            val workspaceId = call.parameters["id"]?.toIntOrNull()
            if (workspaceId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid workspace id")
                return@put
            }
            val request = call.receiveNullable<UpdateWorkspaceRequest>()
            if (request == null || request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                return@put
            }
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@put
            }
            val isOwner = transaction {
                WorkspaceMembers.selectAll()
                    .where {
                        (WorkspaceMembers.workspaceId eq workspaceId) and
                        (WorkspaceMembers.userId eq userId) and
                        (WorkspaceMembers.role eq "OWNER")
                    }.any()
            }
            if (!isOwner) {
                call.respond(HttpStatusCode.Forbidden, "Only workspace owner can edit")
                return@put
            }
            transaction {
                Workspaces.update({ Workspaces.id eq workspaceId }) {
                    it[name] = request.name
                    it[description] = request.description
                }
            }
            call.respond(HttpStatusCode.OK, "Workspace updated")
        }

        // Удалить пространство вместе с задачами, комментариями, вложениями и участниками (только OWNER)
        delete("/{id}") {
            val workspaceId = call.parameters["id"]?.toIntOrNull()
            if (workspaceId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid workspace id")
                return@delete
            }
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@delete
            }
            val isOwner = transaction {
                WorkspaceMembers.selectAll()
                    .where {
                        (WorkspaceMembers.workspaceId eq workspaceId) and
                        (WorkspaceMembers.userId eq userId) and
                        (WorkspaceMembers.role eq "OWNER")
                    }.any()
            }
            if (!isOwner) {
                call.respond(HttpStatusCode.Forbidden, "Only workspace owner can delete the workspace")
                return@delete
            }
            // Сначала удаляем физические файлы всех задач
            val taskIds = transaction {
                Tasks.select(Tasks.id).where { Tasks.workspaceId eq workspaceId }.map { it[Tasks.id] }
            }
            if (taskIds.isNotEmpty()) {
                val attachmentPaths = transaction {
                    TaskAttachments.select(TaskAttachments.storedPath)
                        .where { TaskAttachments.taskId inList taskIds }
                        .map { it[TaskAttachments.storedPath] }
                }
                attachmentPaths.forEach { java.io.File(it).delete() }
            }
            // Каскадное удаление в одной транзакции
            transaction {
                if (taskIds.isNotEmpty()) {
                    TaskAttachments.deleteWhere { TaskAttachments.taskId inList taskIds }
                    TaskComments.deleteWhere   { TaskComments.taskId   inList taskIds }
                    Tasks.deleteWhere          { Tasks.workspaceId eq workspaceId }
                }
                WorkspaceMembers.deleteWhere  { WorkspaceMembers.workspaceId eq workspaceId }
                Workspaces.deleteWhere        { Workspaces.id eq workspaceId }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        // Вступить в приватное пространство по inviteCode
        post("/join") {
            val request = call.receiveNullable<JoinWorkspaceRequest>()
            if (request == null || request.inviteCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                return@post
            }

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@post
            }

            val workspace = transaction {
                Workspaces.selectAll()
                    .where { Workspaces.inviteCode eq request.inviteCode }
                    .limit(1)
                    .firstOrNull()
            }

            if (workspace == null) {
                call.respond(HttpStatusCode.NotFound, "Invite code not found or expired")
                return@post
            }

            val expiry = workspace[Workspaces.inviteCodeExpiresAt]
            if (expiry == null || expiry.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
                call.respond(HttpStatusCode.Gone, "Invite code has expired")
                return@post
            }

            if (workspace[Workspaces.visibility] != "PRIVATE") {
                call.respond(HttpStatusCode.BadRequest, "Workspace is not private")
                return@post
            }

            val workspaceId = workspace[Workspaces.id]

            transaction {
                // добавляем членство (идемпотентно)
                WorkspaceMembers.insertIgnore {
                    it[WorkspaceMembers.workspaceId] = workspaceId
                    it[WorkspaceMembers.userId] = userId
                    it[role] = "MEMBER"
                }
            }

            val model = WorkspaceModel(
                id = workspaceId,
                name = workspace[Workspaces.name],
                description = workspace[Workspaces.description],
                visibility = workspace[Workspaces.visibility],
                inviteCode = null,
                ownerId = workspace[Workspaces.ownerId],
                createdAt = workspace[Workspaces.createdAt].toEpochSecond(ZoneOffset.UTC)
            )

            call.respond(HttpStatusCode.OK, model)
        }
    }
}
