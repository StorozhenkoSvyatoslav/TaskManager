package ru.storozhenko.taskmanager

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import ru.storozhenko.taskmanager.database.DatabaseFactory
import ru.storozhenko.taskmanager.routing.authRouting
import javax.swing.text.AbstractDocument

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)


}

fun Application.module() {
    DatabaseFactory.init()

    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText(text = "DB is connected!", io.ktor.http.ContentType.Text.Plain)
        }
        authRouting()
    }
}