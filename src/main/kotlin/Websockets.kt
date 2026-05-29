package com.example

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.insert
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class ChatMessage(
    val senderId: Int,
    val receiverId: Int,
    val text: String
)

// Хранит все активные подключения
val connections = ConcurrentHashMap<Int, DefaultWebSocketSession>()

fun Application.configureWebSockets() {
    routing {
        webSocket("/chat/{userId}") {
            val userId = call.parameters["userId"]?.toInt()
            if (userId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "userId не указан"))
                return@webSocket
            }

            // Сохраняем подключение
            connections[userId] = this
            println("Пользователь $userId подключился. Всего: ${connections.size}")

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val json = frame.readText()
                        val msg = Json.decodeFromString<ChatMessage>(json)

                        // Сохраняем в БД
                        transaction {
                            Messages.insert {
                                it[senderId] = msg.senderId
                                it[receiverId] = msg.receiverId
                                it[text] = msg.text
                                it[createdAt] = System.currentTimeMillis()
                            }
                        }

                        // Отправляем получателю если он онлайн
                        connections[msg.receiverId]?.send(Frame.Text(json))
                    }
                }
            } finally {
                connections.remove(userId)
                println("Пользователь $userId отключился")
            }
        }
    }
}
