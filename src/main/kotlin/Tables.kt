package com.example

import org.jetbrains.exposed.dao.id.IntIdTable

object Users : IntIdTable("users") {
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 255)
    val createdAt = long("created_at").default(System.currentTimeMillis())
}

object Messages : IntIdTable("messages") {
    val senderId = integer("sender_id").references(Users.id)
    val receiverId = integer("receiver_id").references(Users.id)
    val text = text("text")
    val createdAt = long("created_at").default(System.currentTimeMillis())
}