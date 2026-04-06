package ru.storozhenko.taskmanager.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ru.storozhenko.taskmanager.models.AuthResponse
import ru.storozhenko.taskmanager.models.RegisterRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.storozhenko.taskmanager.repository.UserRepository

fun Route.authRouting() {
    val userRepository = UserRepository()

    route("/auth") {
        post("/register") {
            val request = call.receiveNullable<RegisterRequest>() ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                AuthResponse("", "Invalid request")
            )
            val isRegistered = userRepository.registerUser(request)

            if (isRegistered) {
                // Пока мы возвращаем просто сообщение об успехе. Токены (JWT) мы подключим позже, когда добавим аутентификацию.
                call.respond(HttpStatusCode.Created, AuthResponse("", "User successfully registered"))
            } else {
                call.respond(HttpStatusCode.Conflict, AuthResponse("", "User with this email already exists"))
            }
        }
    }
}