package ru.storozhenko.taskmanager.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.WorkspaceMembers
import ru.storozhenko.taskmanager.database.tables.Workspaces
import ru.storozhenko.taskmanager.models.CreateWorkspaceRequest
import ru.storozhenko.taskmanager.models.JoinWorkspaceRequest
import ru.storozhenko.taskmanager.models.WorkspaceModel
import java.time.ZoneOffset

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

        // Получить inviteCode (только владелец/OWNER)
        get("/{id}/inviteCode") {
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

            val result = transaction {
                val workspaceRow = Workspaces
                    .select(Workspaces.ownerId, Workspaces.inviteCode)
                    .where { Workspaces.id eq workspaceId }
                    .limit(1)
                    .firstOrNull()
                    ?: return@transaction Pair(null, null)

                val ownerId = workspaceRow[Workspaces.ownerId]
                val inviteCode = workspaceRow[Workspaces.inviteCode]

                // 1) прямой владелец по workspaces.owner_id
                if (ownerId == userId) return@transaction Pair(true, inviteCode)

                // 2) или роль OWNER в членстве (на случай, если данные owner_id/членства разъехались)
                val isOwnerMember = WorkspaceMembers
                    .selectAll()
                    .where {
                        (WorkspaceMembers.workspaceId eq workspaceId) and
                            (WorkspaceMembers.userId eq userId) and
                            (WorkspaceMembers.role eq "OWNER")
                    }
                    .limit(1)
                    .any()

                Pair(isOwnerMember, inviteCode)
            }

            val hasAccess = result.first
            val inviteCode = result.second

            if (hasAccess == null) {
                call.respond(HttpStatusCode.NotFound, "Workspace not found")
                return@get
            }

            if (hasAccess != true) {
                call.respond(HttpStatusCode.Forbidden, "No access")
                return@get
            }

            call.respond(HttpStatusCode.OK, mapOf("inviteCode" to inviteCode))
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
                call.respond(HttpStatusCode.NotFound, "Workspace not found")
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
