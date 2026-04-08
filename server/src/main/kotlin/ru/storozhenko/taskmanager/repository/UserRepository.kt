package ru.storozhenko.taskmanager.repository

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import ru.storozhenko.taskmanager.database.tables.Users
import ru.storozhenko.taskmanager.models.LoginRequest
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

    fun verifyUser(request: LoginRequest): Pair<Int, String>? {
        return transaction {
            val user = Users.select { Users.email eq request.email }.singleOrNull()

            if (user != null) {
                val storedHash = user[Users.passwordHash]
                val isPasswordCorrect = BCrypt.verifyer().verify(request.password.toCharArray(), storedHash).verified
                if (isPasswordCorrect) return@transaction Pair(user[Users.id], user[Users.username])
            }
            null
        }
    }
}