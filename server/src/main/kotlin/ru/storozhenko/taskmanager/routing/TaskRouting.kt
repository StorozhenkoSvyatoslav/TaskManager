package ru.storozhenko.taskmanager.routing

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.TaskAttachments
import ru.storozhenko.taskmanager.database.tables.TaskComments
import ru.storozhenko.taskmanager.database.tables.Tasks
import ru.storozhenko.taskmanager.database.tables.WorkspaceMembers
import ru.storozhenko.taskmanager.models.AttachmentModel
import ru.storozhenko.taskmanager.models.CreateTaskRequest
import ru.storozhenko.taskmanager.models.TaskDetailModel
import ru.storozhenko.taskmanager.models.TaskModel
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

private val uploadsDir = File("uploads").also { it.mkdirs() }

private data class PendingFile(val name: String, val bytes: ByteArray, val mimeType: String?)

fun Route.taskRouting() {
    route("/tasks") {

        // GET /tasks — задачи текущего пользователя (без вложений, для списков)
        get {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@get
            }

            val userTasks = transaction {
                Tasks.selectAll().where { Tasks.authorId eq userId }.map { it.toTaskModel() }
            }
            call.respond(HttpStatusCode.OK, userTasks)
        }

        // GET /tasks/{id} — задача с полным списком вложений
        get("/{id}") {
            val taskId = call.parameters["id"]?.toIntOrNull()
            if (taskId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid task id")
                return@get
            }

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@get
            }

            val taskRow = transaction {
                Tasks.selectAll().where { Tasks.id eq taskId }.limit(1).firstOrNull()
            }
            if (taskRow == null) {
                call.respond(HttpStatusCode.NotFound, "Task not found")
                return@get
            }

            val isMember = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq taskRow[Tasks.workspaceId]) and
                        (WorkspaceMembers.userId eq userId)
                }.limit(1).any()
            }
            if (!isMember) {
                call.respond(HttpStatusCode.Forbidden, "No access")
                return@get
            }

            val attachments = transaction {
                TaskAttachments.selectAll()
                    .where { TaskAttachments.taskId eq taskId }
                    .orderBy(TaskAttachments.uploadedAt, SortOrder.ASC)
                    .map { it.toAttachmentModel() }
            }

            call.respond(
                HttpStatusCode.OK,
                TaskDetailModel(
                    id = taskRow[Tasks.id],
                    title = taskRow[Tasks.title],
                    description = taskRow[Tasks.description],
                    status = taskRow[Tasks.status],
                    priority = taskRow[Tasks.priority],
                    authorId = taskRow[Tasks.authorId],
                    workspaceId = taskRow[Tasks.workspaceId],
                    createdAt = taskRow[Tasks.createdAt].toEpochSecond(ZoneOffset.UTC),
                    updatedAt = taskRow[Tasks.updatedAt].toEpochSecond(ZoneOffset.UTC),
                    attachments = attachments,
                )
            )
        }

        // POST /tasks — создать задачу с опциональными файлами
        // Content-Type: multipart/form-data
        // Поля: "task" (JSON CreateTaskRequest), "file" (файлы, повторяемые)
        post {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@post
            }

            val (createRequest, pendingFiles) = call.receiveMultipart().parseTaskMultipart()

            val request = createRequest ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing \"task\" field with JSON task data")
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

            pendingFiles.saveAll(taskId = newTaskId, uploadedBy = userId)

            call.respond(HttpStatusCode.Created, "Task created with ID: $newTaskId")
        }

        // PUT /tasks/{id} — обновить задачу + управление вложениями
        // Content-Type: multipart/form-data
        // Поля: "task" (JSON), "deleteAttachmentIds" (JSON-массив ID для удаления), "file" (новые файлы)
        put("/{id}") {
            val taskId = call.parameters["id"]?.toIntOrNull()
            if (taskId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid task id")
                return@put
            }

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@put
            }

            val (updateRequest, pendingFiles, deleteAttachmentIds) = call.receiveMultipart().parseTaskMultipart()

            val request = updateRequest ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing \"task\" field with JSON task data")
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

            // Удаляем запрошенные вложения
            if (deleteAttachmentIds.isNotEmpty()) {
                val paths = transaction {
                    TaskAttachments.select(TaskAttachments.storedPath)
                        .where {
                            (TaskAttachments.taskId eq taskId) and
                                (TaskAttachments.id inList deleteAttachmentIds)
                        }
                        .map { it[TaskAttachments.storedPath] }
                }
                paths.forEach { File(it).delete() }
                transaction {
                    TaskAttachments.deleteWhere {
                        (TaskAttachments.taskId eq taskId) and (TaskAttachments.id inList deleteAttachmentIds)
                    }
                }
            }

            // Сохраняем новые вложения
            pendingFiles.saveAll(taskId = taskId, uploadedBy = userId)

            call.respond(HttpStatusCode.OK, "Task updated")
        }

        // DELETE /tasks/{id} — удалить задачу вместе с файлами и комментариями
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

            // Сначала удаляем физические файлы, затем всё в одной транзакции
            val attachmentPaths = transaction {
                TaskAttachments.select(TaskAttachments.storedPath)
                    .where { TaskAttachments.taskId eq taskId }
                    .map { it[TaskAttachments.storedPath] }
            }
            attachmentPaths.forEach { File(it).delete() }

            transaction {
                TaskAttachments.deleteWhere { TaskAttachments.taskId eq taskId }
                TaskComments.deleteWhere { TaskComments.taskId eq taskId }
                Tasks.deleteWhere { Tasks.id eq taskId }
            }

            call.respond(HttpStatusCode.NoContent)
        }

        // GET /tasks/workspace/{workspaceId} — задачи конкретного workspace (только участники)
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
                Tasks.selectAll().where { Tasks.workspaceId eq workspaceId }.map { it.toTaskModel() }
            }
            call.respond(HttpStatusCode.OK, tasks)
        }
    }
}

// ─── helpers ────────────────────────────────────────────────────────────────

private data class MultipartTaskResult(
    val taskRequest: CreateTaskRequest?,
    val files: List<PendingFile>,
    val deleteAttachmentIds: List<Int> = emptyList(),
)

private suspend fun MultiPartData.parseTaskMultipart(): MultipartTaskResult {
    var taskRequest: CreateTaskRequest? = null
    val files = mutableListOf<PendingFile>()
    var deleteIds: List<Int> = emptyList()

    forEachPart { part ->
        when {
            part is PartData.FormItem && part.name == "task" ->
                taskRequest = Json.decodeFromString(part.value)

            part is PartData.FormItem && part.name == "deleteAttachmentIds" ->
                deleteIds = Json.decodeFromString(part.value)

            part is PartData.FileItem -> {
                val name = part.originalFileName?.ifBlank { null } ?: "file"
                val bytes = part.streamProvider().readBytes()
                if (bytes.isNotEmpty()) {
                    files.add(PendingFile(name, bytes, part.contentType?.toString()))
                }
            }
        }
        part.dispose()
    }

    return MultipartTaskResult(taskRequest, files, deleteIds)
}

private fun List<PendingFile>.saveAll(taskId: Int, uploadedBy: Int) {
    for (pending in this) {
        val file = File(uploadsDir, "${UUID.randomUUID()}_${pending.name}")
        file.writeBytes(pending.bytes)
        transaction {
            TaskAttachments.insert {
                it[TaskAttachments.taskId] = taskId
                it[TaskAttachments.uploadedBy] = uploadedBy
                it[TaskAttachments.fileName] = pending.name
                it[TaskAttachments.storedPath] = file.absolutePath
                it[TaskAttachments.mimeType] = pending.mimeType
                it[TaskAttachments.fileSize] = pending.bytes.size.toLong()
            }
        }
    }
}

private fun ResultRow.toTaskModel() = TaskModel(
    id = this[Tasks.id],
    title = this[Tasks.title],
    description = this[Tasks.description],
    status = this[Tasks.status],
    priority = this[Tasks.priority],
    authorId = this[Tasks.authorId],
    workspaceId = this[Tasks.workspaceId],
    createdAt = this[Tasks.createdAt].toEpochSecond(ZoneOffset.UTC),
    updatedAt = this[Tasks.updatedAt].toEpochSecond(ZoneOffset.UTC),
)

private fun ResultRow.toAttachmentModel() = AttachmentModel(
    id = this[TaskAttachments.id],
    taskId = this[TaskAttachments.taskId],
    uploadedBy = this[TaskAttachments.uploadedBy],
    fileName = this[TaskAttachments.fileName],
    mimeType = this[TaskAttachments.mimeType],
    fileSize = this[TaskAttachments.fileSize],
    uploadedAt = this[TaskAttachments.uploadedAt].toEpochSecond(ZoneOffset.UTC),
)