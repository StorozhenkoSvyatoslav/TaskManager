package ru.storozhenko.taskmanager.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
import ru.storozhenko.taskmanager.JWT_AUDIENCE
import ru.storozhenko.taskmanager.JWT_ISSUER
import ru.storozhenko.taskmanager.JWT_SECRET
import ru.storozhenko.taskmanager.models.LoginRequest
import ru.storozhenko.taskmanager.repository.UserRepository
import java.util.Date

fun Route.authRouting() {
    val userRepository = UserRepository()

    route("/auth") {

        post("/login") {
            val request = call.receiveNullable<LoginRequest>() ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                AuthResponse("", "Invalid request")
            )

            // Проверяем пользователя
            val username = userRepository.verifyUser(request)

            if (username != null) {
                val token = JWT.create()
                    .withAudience(JWT_AUDIENCE)
                    .withIssuer(JWT_ISSUER)
                    .withClaim("username", username)
                    .withExpiresAt(Date(System.currentTimeMillis() + 60000 * 60 * 24)) // Токен живет 24 часа
                    .sign(Algorithm.HMAC256(JWT_SECRET)) // Подписываем токен

                call.respond(HttpStatusCode.OK, AuthResponse(token, "Login successful"))
            } else {
                call.respond(HttpStatusCode.Unauthorized, AuthResponse("", "Invalid credentials"))
            }
        }

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