package ru.storozhenko.taskmanager.routing

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.TaskAttachments
import ru.storozhenko.taskmanager.database.tables.Tasks
import ru.storozhenko.taskmanager.database.tables.WorkspaceMembers
import ru.storozhenko.taskmanager.models.AttachmentModel
import java.io.File
import java.time.ZoneOffset
import java.util.UUID

private val uploadsDir = File("uploads").also { it.mkdirs() }

fun Route.attachmentRouting() {
    route("/tasks/{taskId}/attachments") {

        get {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid task id")

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid user")

            val taskWorkspaceId = transaction {
                Tasks.select(Tasks.workspaceId).where { Tasks.id eq taskId }.limit(1).firstOrNull()?.get(Tasks.workspaceId)
            } ?: return@get call.respond(HttpStatusCode.NotFound, "Task not found")

            val isMember = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq taskWorkspaceId) and (WorkspaceMembers.userId eq userId)
                }.limit(1).any()
            }
            if (!isMember) return@get call.respond(HttpStatusCode.Forbidden, "No access")

            val attachments = transaction {
                TaskAttachments.selectAll()
                    .where { TaskAttachments.taskId eq taskId }
                    .orderBy(TaskAttachments.uploadedAt, SortOrder.DESC)
                    .map {
                        AttachmentModel(
                            id = it[TaskAttachments.id],
                            taskId = it[TaskAttachments.taskId],
                            uploadedBy = it[TaskAttachments.uploadedBy],
                            fileName = it[TaskAttachments.fileName],
                            mimeType = it[TaskAttachments.mimeType],
                            fileSize = it[TaskAttachments.fileSize],
                            uploadedAt = it[TaskAttachments.uploadedAt].toEpochSecond(ZoneOffset.UTC)
                        )
                    }
            }
            call.respond(HttpStatusCode.OK, attachments)
        }

        post {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid task id")

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid user")

            val taskWorkspaceId = transaction {
                Tasks.select(Tasks.workspaceId).where { Tasks.id eq taskId }.limit(1).firstOrNull()?.get(Tasks.workspaceId)
            } ?: return@post call.respond(HttpStatusCode.NotFound, "Task not found")

            val isMember = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq taskWorkspaceId) and (WorkspaceMembers.userId eq userId)
                }.limit(1).any()
            }
            if (!isMember) return@post call.respond(HttpStatusCode.Forbidden, "Only workspace members can upload files")

            val multipart = call.receiveMultipart()
            var savedAttachmentId: Int? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val originalName = part.originalFileName?.ifBlank { null } ?: "file"
                    val storedName = "${UUID.randomUUID()}_$originalName"
                    val file = File(uploadsDir, storedName)

                    val bytes = part.streamProvider().readBytes()
                    file.writeBytes(bytes)

                    savedAttachmentId = transaction {
                        TaskAttachments.insert {
                            it[TaskAttachments.taskId] = taskId
                            it[uploadedBy] = userId
                            it[fileName] = originalName
                            it[storedPath] = file.absolutePath
                            it[mimeType] = part.contentType?.toString()
                            it[fileSize] = bytes.size.toLong()
                        } get TaskAttachments.id
                    }
                }
                part.dispose()
            }

            if (savedAttachmentId == null) {
                call.respond(HttpStatusCode.BadRequest, "No file provided in the request")
            } else {
                call.respond(HttpStatusCode.Created, "Attachment uploaded with ID: $savedAttachmentId")
            }
        }

        get("/{attachmentId}/download") {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid task id")
            val attachmentId = call.parameters["attachmentId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid attachment id")

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid user")

            val taskWorkspaceId = transaction {
                Tasks.select(Tasks.workspaceId).where { Tasks.id eq taskId }.limit(1).firstOrNull()?.get(Tasks.workspaceId)
            } ?: return@get call.respond(HttpStatusCode.NotFound, "Task not found")

            val isMember = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq taskWorkspaceId) and (WorkspaceMembers.userId eq userId)
                }.limit(1).any()
            }
            if (!isMember) return@get call.respond(HttpStatusCode.Forbidden, "No access")

            val attachment = transaction {
                TaskAttachments.selectAll()
                    .where { (TaskAttachments.id eq attachmentId) and (TaskAttachments.taskId eq taskId) }
                    .limit(1).firstOrNull()
            } ?: return@get call.respond(HttpStatusCode.NotFound, "Attachment not found")

            val file = File(attachment[TaskAttachments.storedPath])
            if (!file.exists()) return@get call.respond(HttpStatusCode.NotFound, "File not found on server")

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(ContentDisposition.Parameters.FileName, attachment[TaskAttachments.fileName])
                    .toString()
            )
            call.respondFile(file)
        }

        delete("/{attachmentId}") {
            val taskId = call.parameters["taskId"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid task id")
            val attachmentId = call.parameters["attachmentId"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid attachment id")

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, "Invalid user")

            val attachment = transaction {
                TaskAttachments.selectAll()
                    .where { (TaskAttachments.id eq attachmentId) and (TaskAttachments.taskId eq taskId) }
                    .limit(1).firstOrNull()
            } ?: return@delete call.respond(HttpStatusCode.NotFound, "Attachment not found")

            val taskWorkspaceId = transaction {
                Tasks.select(Tasks.workspaceId).where { Tasks.id eq taskId }.limit(1).firstOrNull()?.get(Tasks.workspaceId)
            } ?: return@delete call.respond(HttpStatusCode.NotFound, "Task not found")

            val isOwner = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq taskWorkspaceId) and
                        (WorkspaceMembers.userId eq userId) and
                        (WorkspaceMembers.role eq "OWNER")
                }.limit(1).any()
            }
            val isUploader = attachment[TaskAttachments.uploadedBy] == userId

            if (!isUploader && !isOwner) {
                return@delete call.respond(HttpStatusCode.Forbidden, "Only the uploader or workspace owner can delete this file")
            }

            File(attachment[TaskAttachments.storedPath]).delete()
            transaction {
                TaskAttachments.deleteWhere { TaskAttachments.id eq attachmentId }
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}