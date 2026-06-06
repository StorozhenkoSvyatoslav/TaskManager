package ru.storozhenko.taskmanager.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.TaskAttachments
import ru.storozhenko.taskmanager.database.tables.TaskComments
import ru.storozhenko.taskmanager.database.tables.Tasks
import ru.storozhenko.taskmanager.database.tables.WorkspaceMembers
import ru.storozhenko.taskmanager.models.WorkspaceStats

fun Route.statsRouting() {
    route("/workspaces/{workspaceId}/stats") {

        get {
            val workspaceId = call.parameters["workspaceId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid workspace id")

            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid user")

            val isMember = transaction {
                WorkspaceMembers.selectAll().where {
                    (WorkspaceMembers.workspaceId eq workspaceId) and (WorkspaceMembers.userId eq userId)
                }.limit(1).any()
            }
            if (!isMember) return@get call.respond(HttpStatusCode.Forbidden, "No access")

            val stats = transaction {
                val statusCount = Tasks.id.count()
                val tasksByStatus = Tasks
                    .select(Tasks.status, statusCount)
                    .where { Tasks.workspaceId eq workspaceId }
                    .groupBy(Tasks.status)
                    .associate { it[Tasks.status] to it[statusCount].toInt() }

                val priorityCount = Tasks.id.count()
                val tasksByPriority = Tasks
                    .select(Tasks.priority, priorityCount)
                    .where { Tasks.workspaceId eq workspaceId }
                    .groupBy(Tasks.priority)
                    .associate { it[Tasks.priority] to it[priorityCount].toInt() }

                val totalTasks = Tasks.selectAll()
                    .where { Tasks.workspaceId eq workspaceId }
                    .count().toInt()

                val totalComments = TaskComments
                    .join(Tasks, JoinType.INNER, TaskComments.taskId, Tasks.id)
                    .selectAll()
                    .where { Tasks.workspaceId eq workspaceId }
                    .count().toInt()

                val totalAttachments = TaskAttachments
                    .join(Tasks, JoinType.INNER, TaskAttachments.taskId, Tasks.id)
                    .selectAll()
                    .where { Tasks.workspaceId eq workspaceId }
                    .count().toInt()

                val totalMembers = WorkspaceMembers.selectAll()
                    .where { WorkspaceMembers.workspaceId eq workspaceId }
                    .count().toInt()

                WorkspaceStats(
                    workspaceId = workspaceId,
                    totalTasks = totalTasks,
                    tasksByStatus = tasksByStatus,
                    tasksByPriority = tasksByPriority,
                    totalComments = totalComments,
                    totalMembers = totalMembers,
                    totalAttachments = totalAttachments,
                )
            }

            call.respond(HttpStatusCode.OK, stats)
        }
    }
}