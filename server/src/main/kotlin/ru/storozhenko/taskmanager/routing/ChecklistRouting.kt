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
import ru.storozhenko.taskmanager.database.tables.TaskChecklists
import ru.storozhenko.taskmanager.database.tables.Tasks
import ru.storozhenko.taskmanager.database.tables.WorkspaceMembers
import ru.storozhenko.taskmanager.models.ChecklistItemModel
import ru.storozhenko.taskmanager.models.CreateChecklistItemRequest
import ru.storozhenko.taskmanager.models.UpdateChecklistItemRequest
import java.time.ZoneOffset

fun Route.checklistRouting() {
    route("/tasks/{taskId}/checklist") {

        get {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid task id")

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid user")

            val workspaceId = transaction {
                Tasks.select(Tasks.workspaceId).where { Tasks.id eq taskId }.firstOrNull()?.get(Tasks.workspaceId)
            } ?: return@get call.respond(HttpStatusCode.NotFound, "Task not found")

            val isMember = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq workspaceId) and (WorkspaceMembers.userId eq userId)
                }.limit(1).any()
            }
            if (!isMember) return@get call.respond(HttpStatusCode.Forbidden, "No access")

            val items = transaction {
                TaskChecklists.selectAll()
                    .where { TaskChecklists.taskId eq taskId }
                    .orderBy(TaskChecklists.createdAt, SortOrder.ASC)
                    .map { it.toChecklistItemModel() }
            }
            call.respond(HttpStatusCode.OK, items)
        }

        post {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid task id")

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid user")

            val workspaceId = transaction {
                Tasks.select(Tasks.workspaceId).where { Tasks.id eq taskId }.firstOrNull()?.get(Tasks.workspaceId)
            } ?: return@post call.respond(HttpStatusCode.NotFound, "Task not found")

            val isMember = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq workspaceId) and (WorkspaceMembers.userId eq userId)
                }.limit(1).any()
            }
            if (!isMember) return@post call.respond(HttpStatusCode.Forbidden, "Only workspace members can add checklist items")

            val request = call.receiveNullable<CreateChecklistItemRequest>()
            if (request == null || request.text.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Checklist item text must not be empty")
            }

            val newItem = transaction {
                val newId = TaskChecklists.insert {
                    it[TaskChecklists.taskId] = taskId
                    it[text] = request.text.trim()
                    it[isCompleted] = false
                } get TaskChecklists.id

                TaskChecklists.selectAll().where { TaskChecklists.id eq newId }.first().toChecklistItemModel()
            }
            call.respond(HttpStatusCode.Created, newItem)
        }

        put("/{itemId}") {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid task id")
            val itemId = call.parameters["itemId"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid item id")

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
                ?: return@put call.respond(HttpStatusCode.Unauthorized, "Invalid user")

            val workspaceId = transaction {
                Tasks.select(Tasks.workspaceId).where { Tasks.id eq taskId }.firstOrNull()?.get(Tasks.workspaceId)
            } ?: return@put call.respond(HttpStatusCode.NotFound, "Task not found")

            val isMember = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq workspaceId) and (WorkspaceMembers.userId eq userId)
                }.limit(1).any()
            }
            if (!isMember) return@put call.respond(HttpStatusCode.Forbidden, "No access")

            val request = call.receive<UpdateChecklistItemRequest>()

            val updated = transaction {
                TaskChecklists.update({
                    (TaskChecklists.id eq itemId) and (TaskChecklists.taskId eq taskId)
                }) {
                    it[isCompleted] = request.isCompleted
                }
            }
            if (updated == 0) return@put call.respond(HttpStatusCode.NotFound, "Item not found")

            call.respond(HttpStatusCode.OK, "Updated")
        }

        delete("/{itemId}") {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid task id")
            val itemId = call.parameters["itemId"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid item id")

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, "Invalid user")

            val workspaceId = transaction {
                Tasks.select(Tasks.workspaceId).where { Tasks.id eq taskId }.firstOrNull()?.get(Tasks.workspaceId)
            } ?: return@delete call.respond(HttpStatusCode.NotFound, "Task not found")

            val isMember = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq workspaceId) and (WorkspaceMembers.userId eq userId)
                }.limit(1).any()
            }
            if (!isMember) return@delete call.respond(HttpStatusCode.Forbidden, "No access")

            transaction {
                TaskChecklists.deleteWhere {
                    (TaskChecklists.id eq itemId) and (TaskChecklists.taskId eq taskId)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

internal fun ResultRow.toChecklistItemModel() = ChecklistItemModel(
    id          = this[TaskChecklists.id],
    taskId      = this[TaskChecklists.taskId],
    text        = this[TaskChecklists.text],
    isCompleted = this[TaskChecklists.isCompleted],
    createdAt   = this[TaskChecklists.createdAt].toEpochSecond(ZoneOffset.UTC),
)
