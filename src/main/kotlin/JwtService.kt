package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtService {
    private const val SECRET = "my_super_secret_key_123"
    private const val ISSUER = "chat-server"
    private const val EXPIRES_IN = 86400000L // 24 часа

    private val algorithm = Algorithm.HMAC256(SECRET)

    // Создаём токен
    fun generateToken(userId: Int, username: String): String {
        return JWT.create()
            .withIssuer(ISSUER)
            .withClaim("userId", userId)
            .withClaim("username", username)
            .withExpiresAt(Date(System.currentTimeMillis() + EXPIRES_IN))
            .sign(algorithm)
    }

    // Проверяем токен
    fun verifyToken(token: String): Int? {
        return try {
            val decoded = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build()
                .verify(token)
            decoded.getClaim("userId").asInt()
        } catch (e: Exception) {
            null
        }
    }
}