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
import ru.storozhenko.taskmanager.database.tables.TaskComments
import ru.storozhenko.taskmanager.database.tables.Tasks
import ru.storozhenko.taskmanager.database.tables.Users
import ru.storozhenko.taskmanager.database.tables.WorkspaceMembers
import ru.storozhenko.taskmanager.models.CommentModel
import ru.storozhenko.taskmanager.models.CreateCommentRequest
import java.time.ZoneOffset

fun Route.commentRouting() {
    route("/tasks/{taskId}/comments") {

        get {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
            if (taskId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid task id")
                return@get
            }

            val taskExists = transaction {
                Tasks.selectAll().where { Tasks.id eq taskId }.limit(1).any()
            }
            if (!taskExists) {
                call.respond(HttpStatusCode.NotFound, "Task not found")
                return@get
            }

            val comments = transaction {
                (TaskComments innerJoin Users)
                    .select(
                        TaskComments.id,
                        TaskComments.taskId,
                        TaskComments.authorId,
                        Users.username,
                        TaskComments.content,
                        TaskComments.createdAt
                    )
                    .where { TaskComments.taskId eq taskId }
                    .orderBy(TaskComments.createdAt, SortOrder.ASC)
                    .map {
                        CommentModel(
                            id = it[TaskComments.id],
                            taskId = it[TaskComments.taskId],
                            authorId = it[TaskComments.authorId],
                            authorUsername = it[Users.username],
                            content = it[TaskComments.content],
                            createdAt = it[TaskComments.createdAt].toEpochSecond(ZoneOffset.UTC)
                        )
                    }
            }

            call.respond(HttpStatusCode.OK, comments)
        }

        post {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
            if (taskId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid task id")
                return@post
            }

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@post
            }

            val request = call.receiveNullable<CreateCommentRequest>()
            if (request == null || request.content.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Comment content must not be empty")
                return@post
            }

            val taskWorkspaceId = transaction {
                Tasks.select(Tasks.workspaceId).where { Tasks.id eq taskId }.limit(1).firstOrNull()?.get(Tasks.workspaceId)
            }
            if (taskWorkspaceId == null) {
                call.respond(HttpStatusCode.NotFound, "Task not found")
                return@post
            }

            val isMember = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq taskWorkspaceId) and (WorkspaceMembers.userId eq userId)
                }.limit(1).any()
            }
            if (!isMember) {
                call.respond(HttpStatusCode.Forbidden, "Only workspace members can comment")
                return@post
            }

            val newCommentId = transaction {
                TaskComments.insert {
                    it[TaskComments.taskId] = taskId
                    it[authorId] = userId
                    it[content] = request.content
                } get TaskComments.id
            }

            call.respond(HttpStatusCode.Created, "Comment created with ID: $newCommentId")
        }

        delete("/{commentId}") {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
            val commentId = call.parameters["commentId"]?.toIntOrNull()
            if (taskId == null || commentId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid task or comment id")
                return@delete
            }

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@delete
            }

            val comment = transaction {
                TaskComments.selectAll()
                    .where { (TaskComments.id eq commentId) and (TaskComments.taskId eq taskId) }
                    .limit(1)
                    .firstOrNull()
            }

            if (comment == null) {
                call.respond(HttpStatusCode.NotFound, "Comment not found")
                return@delete
            }

            if (comment[TaskComments.authorId] != userId) {
                call.respond(HttpStatusCode.Forbidden, "Only the author can delete their comment")
                return@delete
            }

            transaction {
                TaskComments.deleteWhere { TaskComments.id eq commentId }
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}