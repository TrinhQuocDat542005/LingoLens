package com.quocdat.lingolens.data.remote.dto

data class ApiEnvelope<T>(
    val success: Boolean,
    val message: String,
    val data: T?
)

data class ApiErrorDto(
    val success: Boolean = false,
    val code: String = "UNKNOWN_ERROR",
    val message: String = "Đã xảy ra lỗi",
    val errors: List<String> = emptyList()
)

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, val name: String)
data class RefreshRequest(val refreshToken: String)
data class LogoutRequest(val refreshToken: String)

data class TokenDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val email: String,
    val name: String,
    val roles: List<String>
)

data class UserProfileDto(
    val id: Long,
    val email: String,
    val name: String,
    val targetLevel: String,
    val streakDays: Int,
    val dailyGoal: Int,
    val roles: List<String>
)

data class UpdateProfileRequest(val name: String, val targetLevel: String, val dailyGoal: Int)
