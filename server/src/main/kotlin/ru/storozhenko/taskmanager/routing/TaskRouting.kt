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
import ru.storozhenko.taskmanager.database.tables.Tasks
import ru.storozhenko.taskmanager.models.CreateTaskRequest
import ru.storozhenko.taskmanager.models.TaskModel
import java.time.ZoneOffset
import kotlin.text.get
import kotlin.text.insert
import kotlin.text.set

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
                // Временно получаем просто все задачи пользователя ебаное говно надо поменять нахуй
                Tasks.selectAll().where { Tasks.authorId eq userId }.map {
                    TaskModel(
                        id = it[Tasks.id],
                        title = it[Tasks.title],
                        description = it[Tasks.description],
                        status = it[Tasks.status],
                        priority = it[Tasks.priority],
                        authorId = it[Tasks.authorId],
                        workspaceId = it[Tasks.workspaceId], // <-- Добавили
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

            val newTaskId = transaction {
                Tasks.insert {
                    it[title] = request.title
                    it[description] = request.description
                    it[status] = request.status
                    it[priority] = request.priority
                    it[authorId] = userId
                    it[workspaceId] = request.workspaceId // <-- Сохраняем ID пространства
                } get Tasks.id
            }

            call.respond(HttpStatusCode.Created, "Task created with ID: $newTaskId")
        }
    }
}