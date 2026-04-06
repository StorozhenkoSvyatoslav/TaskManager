package ru.storozhenko.taskmanager.repository

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.Users
import ru.storozhenko.taskmanager.models.RegisterRequest

class UserRepository {
    fun registerUser(request: RegisterRequest): Boolean {
        return transaction {
            val hashedPassword = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())

            try {
                Users.insert {
                    it[username] = request.username
                    it[email] = request.email
                    it[passwordHash] = hashedPassword
                    it[role] = "USER"
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}