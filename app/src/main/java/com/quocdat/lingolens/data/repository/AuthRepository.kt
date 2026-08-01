package com.quocdat.lingolens.data.repository

import com.google.gson.Gson
import com.quocdat.lingolens.data.local.SessionStorage
import com.quocdat.lingolens.data.remote.ApiException
import com.quocdat.lingolens.data.remote.api.AuthApi
import com.quocdat.lingolens.data.remote.api.UserApi
import com.quocdat.lingolens.data.remote.dto.*
import com.quocdat.lingolens.data.remote.toUserMessage
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val authApi: AuthApi,
    private val userApi: UserApi,
    private val storage: SessionStorage,
    private val gson: Gson
) {
    val session: Flow<com.quocdat.lingolens.data.local.StoredSession?> = storage.session

    suspend fun register(name: String, email: String, password: String): Result<Unit> = apiCall {
        authApi.register(RegisterRequest(email.trim(), password, name.trim()))
        Unit
    }

    suspend fun login(email: String, password: String): Result<UserProfileDto> = apiCall {
        val tokens = authApi.login(LoginRequest(email.trim(), password)).data
            ?: throw ApiException("EMPTY_RESPONSE", "Máy chủ không trả về phiên đăng nhập.")
        storage.save(tokens)
        userApi.getMe().data ?: throw ApiException("EMPTY_PROFILE", "Không tải được hồ sơ người dùng.")
    }

    suspend fun restoreSession(): Result<UserProfileDto?> {
        if (storage.current() == null) return Result.success(null)
        return apiCall { userApi.getMe().data ?: throw ApiException("EMPTY_PROFILE", "Không tải được hồ sơ.") }
            .onFailure { storage.clear() }
    }

    suspend fun updateProfile(name: String, level: String, dailyGoal: Int): Result<UserProfileDto> = apiCall {
        userApi.updateMe(UpdateProfileRequest(name, level, dailyGoal)).data
            ?: throw ApiException("EMPTY_PROFILE", "Không tải được hồ sơ.")
    }

    suspend fun logout(): Result<Unit> {
        val session = storage.current()
        return try {
            if (session != null) authApi.logout(LogoutRequest(session.refreshToken))
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(ApiException("LOGOUT_FAILED", error.toUserMessage(gson)))
        } finally {
            storage.clear()
        }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(ApiException("API_ERROR", error.toUserMessage(gson)))
    }
}
