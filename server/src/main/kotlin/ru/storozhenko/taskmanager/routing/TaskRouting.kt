package ru.storozhenko.taskmanager.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.Tasks
import ru.storozhenko.taskmanager.database.tables.WorkspaceMembers
import ru.storozhenko.taskmanager.models.CreateTaskRequest
import ru.storozhenko.taskmanager.models.TaskModel
import java.time.LocalDateTime
import java.time.ZoneOffset

fun Route.taskRouting() {
    route("/tasks") {
        get {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@get
            }

            val userTasks = transaction {
                Tasks.selectAll().where { Tasks.authorId eq userId }.map {
                    TaskModel(
                        id = it[Tasks.id],
                        title = it[Tasks.title],
                        description = it[Tasks.description],
                        status = it[Tasks.status],
                        priority = it[Tasks.priority],
                        authorId = it[Tasks.authorId],
                        workspaceId = it[Tasks.workspaceId],
                        createdAt = it[Tasks.createdAt].toEpochSecond(ZoneOffset.UTC),
                        updatedAt = it[Tasks.updatedAt].toEpochSecond(ZoneOffset.UTC)
                    )
                }
            }

            call.respond(HttpStatusCode.OK, userTasks)
        }

        post {
            val request = call.receiveNullable<CreateTaskRequest>()
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

            val isOwner = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq request.workspaceId) and
                        (WorkspaceMembers.userId eq userId) and
                        (WorkspaceMembers.role eq "OWNER")
                }.limit(1).any()
            }

            if (!isOwner) {
                call.respond(HttpStatusCode.Forbidden, "Only workspace owner can create tasks")
                return@post
            }

            val newTaskId = transaction {
                Tasks.insert {
                    it[title] = request.title
                    it[description] = request.description
                    it[status] = request.status
                    it[priority] = request.priority
                    it[authorId] = userId
                    it[workspaceId] = request.workspaceId
                } get Tasks.id
            }

            call.respond(HttpStatusCode.Created, "Task created with ID: $newTaskId")
        }

        put("/{id}") {
            val taskId = call.parameters["id"]?.toIntOrNull()
            if (taskId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid task id")
                return@put
            }

            val request = call.receiveNullable<CreateTaskRequest>()
            if (request == null) {
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
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq request.workspaceId) and
                        (WorkspaceMembers.userId eq userId) and
                        (WorkspaceMembers.role eq "OWNER")
                }.limit(1).any()
            }

            if (!isOwner) {
                call.respond(HttpStatusCode.Forbidden, "Only workspace owner can update tasks")
                return@put
            }

            val updatedCount = transaction {
                Tasks.update({ (Tasks.id eq taskId) and (Tasks.authorId eq userId) }) {
                    it[title] = request.title
                    it[description] = request.description
                    it[status] = request.status
                    it[priority] = request.priority
                    it[workspaceId] = request.workspaceId
                    it[updatedAt] = LocalDateTime.now()
                }
            }

            if (updatedCount == 0) {
                call.respond(HttpStatusCode.NotFound, "Task not found")
                return@put
            }

            call.respond(HttpStatusCode.OK, "Task updated")
        }

        delete("/{id}") {
            val taskId = call.parameters["id"]?.toIntOrNull()
            if (taskId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid task id")
                return@delete
            }

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@delete
            }

            // узнаем workspace задачи и проверяем OWNER
            val taskWorkspaceId = transaction {
                Tasks.select(Tasks.workspaceId).where { Tasks.id eq taskId }.limit(1).firstOrNull()?.get(Tasks.workspaceId)
            }

            if (taskWorkspaceId == null) {
                call.respond(HttpStatusCode.NotFound, "Task not found")
                return@delete
            }

            val isOwner = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq taskWorkspaceId) and
                        (WorkspaceMembers.userId eq userId) and
                        (WorkspaceMembers.role eq "OWNER")
                }.limit(1).any()
            }

            if (!isOwner) {
                call.respond(HttpStatusCode.Forbidden, "Only workspace owner can delete tasks")
                return@delete
            }

            val deletedCount = transaction {
                // удаляем задачу в рамках workspace; authorId уже не важен (важнее роль)
                Tasks.deleteWhere { (Tasks.id eq taskId) and (Tasks.workspaceId eq taskWorkspaceId) }
            }

            if (deletedCount == 0) {
                call.respond(HttpStatusCode.NotFound, "Task not found")
                return@delete
            }

            call.respond(HttpStatusCode.NoContent)
        }

        // Получить задачи конкретного пространства (только участники)
        get("/workspace/{workspaceId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@get
            }

            val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
            if (workspaceId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid workspace id")
                return@get
            }

            val isMember = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq workspaceId) and (WorkspaceMembers.userId eq userId)
                }.limit(1).any()
            }

            if (!isMember) {
                call.respond(HttpStatusCode.Forbidden, "No access")
                return@get
            }

            val tasks = transaction {
                Tasks.selectAll().where { Tasks.workspaceId eq workspaceId }.map {
                    TaskModel(
                        id = it[Tasks.id],
                        title = it[Tasks.title],
                        description = it[Tasks.description],
                        status = it[Tasks.status],
                        priority = it[Tasks.priority],
                        authorId = it[Tasks.authorId],
                        workspaceId = it[Tasks.workspaceId],
                        createdAt = it[Tasks.createdAt].toEpochSecond(ZoneOffset.UTC),
                        updatedAt = it[Tasks.updatedAt].toEpochSecond(ZoneOffset.UTC)
                    )
                }
            }

            call.respond(HttpStatusCode.OK, tasks)
        }
    }
}