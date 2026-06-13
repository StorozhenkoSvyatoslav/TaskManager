package ru.storozhenko.taskmanager

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.storozhenko.taskmanager.database.DatabaseFactory
import ru.storozhenko.taskmanager.routing.adminRouting
import ru.storozhenko.taskmanager.routing.attachmentRouting
import ru.storozhenko.taskmanager.routing.authRouting
import ru.storozhenko.taskmanager.routing.checklistRouting
import ru.storozhenko.taskmanager.routing.commentRouting
import ru.storozhenko.taskmanager.routing.statsRouting
import ru.storozhenko.taskmanager.routing.taskRouting
import ru.storozhenko.taskmanager.routing.userRouting
import ru.storozhenko.taskmanager.routing.workspaceRouting

val SERVER_PORT: Int = System.getenv("PORT")?.toInt() ?: 8081
val JWT_SECRET: String = System.getenv("JWT_SECRET") ?: "my-secret-key-for-task-manager"
val JWT_ISSUER: String = System.getenv("JWT_ISSUER") ?: "http://localhost:8081"
val JWT_AUDIENCE: String = System.getenv("JWT_AUDIENCE") ?: "task-manager-client"

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)


}

fun Application.module() {
    DatabaseFactory.init()

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Origin)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        maxAgeInSeconds = 3600
    }

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

        // Маршруты для регистрации и авторизации (доступны всем)
        authRouting()

        // Применяем JWT авторизацию ко всем маршрутам внутри блока
        authenticate("auth-jwt") {
            taskRouting()
            workspaceRouting()
            commentRouting()
            checklistRouting()
            attachmentRouting()
            statsRouting()
            userRouting()
            adminRouting()
        }
    }
}