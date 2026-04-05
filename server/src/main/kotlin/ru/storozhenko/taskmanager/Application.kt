package ru.storozhenko.taskmanager

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.storozhenko.taskmanager.database.DatabaseFactory

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)


}

fun Application.module() {
    DatabaseFactory.init()

    routing {
        get("/") {
            call.respondText(text = "DB is connected!", io.ktor.http.ContentType.Text.Plain)
        }
    }
}