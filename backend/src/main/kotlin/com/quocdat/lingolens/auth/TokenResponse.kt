package com.quocdat.lingolens.auth

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val email: String,
    val name: String,
    val roles: List<String>
)
