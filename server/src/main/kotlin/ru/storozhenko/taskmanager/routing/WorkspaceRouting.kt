package ru.storozhenko.taskmanager.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.Workspaces
import ru.storozhenko.taskmanager.models.CreateWorkspaceRequest
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

            val newWorkspaceId = transaction {
                Workspaces.insert {
                    it[name] = request.name
                    it[description] = request.description
                    it[visibility] = request.visibility
                    it[inviteCode] = request.inviteCode
                    it[ownerId] = userId
                } get Workspaces.id
            }

            call.respond(HttpStatusCode.Created, "Workspace created with ID: $newWorkspaceId")
        }
    }
}
