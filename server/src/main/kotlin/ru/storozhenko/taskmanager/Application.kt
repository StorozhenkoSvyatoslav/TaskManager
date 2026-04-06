package ru.storozhenko.taskmanager

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import ru.storozhenko.taskmanager.database.DatabaseFactory
import ru.storozhenko.taskmanager.routing.authRouting
import javax.swing.text.AbstractDocument

//надо будет вынести в файлы конфигурации
const val JWT_SECRET = "my-secret-key-for-task-manager"
const val JWT_ISSUER = "http://localhost:8081"
const val JWT_AUDIENCE = "task-manager-client"

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)


}

fun Application.module() {
    DatabaseFactory.init()

    install(ContentNegotiation) {
        json()
    }

    install(Authentication) {
        jwt("auth-jwt") { // "auth-jwt" — это название схемы авторизации
            realm = "Task Manager Server"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(JWT_SECRET))
                    .withAudience(JWT_AUDIENCE)
                    .withIssuer(JWT_ISSUER)
                    .build()
            )
            validate { credential ->
                // Если токен валидный, и в нем передан username, то пускаем
                if (credential.payload.getClaim("username").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    routing {
        get("/") {
            call.respondText(text = "DB is connected!", io.ktor.http.ContentType.Text.Plain)
        }
        authRouting()
    }
}