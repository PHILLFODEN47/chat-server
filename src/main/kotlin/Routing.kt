package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

@Serializable
data class RegisterRequest(val username: String, val password: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class UserResponse(val id: Int, val username: String)

@Serializable
data class MessageResponse(val id: Int, val senderId: Int, val receiverId: Int, val text: String)

@Serializable
data class LoginResponse(val token: String, val userId: Int, val username: String)

fun Application.configureRouting() {
    routing {

        get("/") {
            call.respondText("Сервер чата работает! 🚀")
        }

        post("/register") {
            val request = call.receive<RegisterRequest>()
            val exists = transaction {
                Users.select { Users.username eq request.username }.count() > 0
            }
            if (exists) {
                call.respond(HttpStatusCode.Conflict, "Пользователь уже существует")
                return@post
            }
            val hashedPassword = BCrypt.hashpw(request.password, BCrypt.gensalt())
            val userId = transaction {
                Users.insertAndGetId {
                    it[username] = request.username
                    it[password] = hashedPassword
                }
            }
            call.respond(HttpStatusCode.Created, UserResponse(userId.value, request.username))
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = transaction {
                Users.select { Users.username eq request.username }.firstOrNull()
            }
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "Пользователь не найден")
                return@post
            }
            val passwordMatch = BCrypt.checkpw(request.password, user[Users.password])
            if (!passwordMatch) {
                call.respond(HttpStatusCode.Unauthorized, "Неверный пароль")
                return@post
            }
            val token = JwtService.generateToken(user[Users.id].value, user[Users.username])
            call.respond(LoginResponse(token, user[Users.id].value, user[Users.username]))
        }

        post("/messages") {
            val senderId = call.request.queryParameters["senderId"]?.toInt()
            val receiverId = call.request.queryParameters["receiverId"]?.toInt()
            val text = call.request.queryParameters["text"]
            if (senderId == null || receiverId == null || text == null) {
                call.respond(HttpStatusCode.BadRequest, "Не все параметры указаны")
                return@post
            }
            val messageId = transaction {
                Messages.insertAndGetId {
                    it[Messages.senderId] = senderId
                    it[Messages.receiverId] = receiverId
                    it[Messages.text] = text
                    it[Messages.createdAt] = System.currentTimeMillis()
                }
            }
            call.respond(HttpStatusCode.Created, mapOf("id" to messageId.value))
        }

        get("/messages") {
            val userId1 = call.request.queryParameters["userId1"]?.toInt()
            val userId2 = call.request.queryParameters["userId2"]?.toInt()
            if (userId1 == null || userId2 == null) {
                call.respond(HttpStatusCode.BadRequest, "Укажи userId1 и userId2")
                return@get
            }
            val msgs = transaction {
                Messages.select {
                    (Messages.senderId eq userId1 and (Messages.receiverId eq userId2)) or
                            (Messages.senderId eq userId2 and (Messages.receiverId eq userId1))
                }.orderBy(Messages.createdAt).map {
                    MessageResponse(
                        id = it[Messages.id].value,
                        senderId = it[Messages.senderId],
                        receiverId = it[Messages.receiverId],
                        text = it[Messages.text]
                    )
                }
            }
            call.respond(msgs)
        }

        get("/users") {
            val users = transaction {
                Users.selectAll().map {
                    UserResponse(it[Users.id].value, it[Users.username])
                }
            }
            call.respond(users)
        }
    }
}