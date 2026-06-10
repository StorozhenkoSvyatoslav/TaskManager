package ru.storozhenko.taskmanager.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.Users
import ru.storozhenko.taskmanager.models.UserProfileModel

fun Route.userRouting() {
    route("/users") {
        get("/me") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("id")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid user")
                return@get
            }
            val user = transaction {
                Users.select { Users.id eq userId }.singleOrNull()
            }
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@get
            }
            call.respond(
                HttpStatusCode.OK,
                UserProfileModel(
                    id = user[Users.id],
                    username = user[Users.username],
                    email = user[Users.email],
                    systemRole = user[Users.role],
                    isBanned = user[Users.isBanned]
                )
            )
        }
    }
}
