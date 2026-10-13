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
        val expected = requireNotNull(storage.current())
        (userApi.getMe().data ?: throw ApiException("EMPTY_PROFILE", "Không tải được hồ sơ người dùng.")).also { storage.saveProfile(it, expected) }
    }

    suspend fun restoreSession(): Result<UserProfileDto?> {
        val expected = storage.current() ?: return Result.success(null)
        return try {
            val profile = userApi.getMe().data ?: throw ApiException("EMPTY_PROFILE", "Không tải được hồ sơ.")
            storage.saveProfile(profile, expected)
            Result.success(profile)
        } catch (ex: kotlinx.coroutines.CancellationException) { throw ex
        } catch (ex: retrofit2.HttpException) {
            if (ex.code() == 401 || ex.code() == 403) storage.clear()
            Result.failure(ApiException("RESTORE_FAILED", ex.toUserMessage(gson)))
        } catch (ex: java.io.IOException) {
            val cached = storage.cachedProfile()
            if (cached != null && storage.current() != null) Result.success(cached)
            else Result.failure(ApiException("RESTORE_FAILED", ex.toUserMessage(gson)))
        } catch (ex: Exception) {
            Result.failure(ApiException("RESTORE_FAILED", ex.toUserMessage(gson)))
        }
    }

    suspend fun updateProfile(name: String, level: String, dailyGoal: Int): Result<UserProfileDto> = apiCall {
        val expected = requireNotNull(storage.current())
        (userApi.updateMe(UpdateProfileRequest(name, level, dailyGoal)).data
            ?: throw ApiException("EMPTY_PROFILE", "Không tải được hồ sơ.")).also { storage.saveProfile(it, expected) }
    }

    suspend fun logout(): Result<Unit> {
        val session = storage.current()
        return try {
            if (session != null) authApi.logout(LogoutRequest(session.refreshToken))
            Result.success(Unit)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error
        } catch (error: Exception) {
            Result.failure(ApiException("LOGOUT_FAILED", error.toUserMessage(gson)))
        } finally {
            storage.clear()
        }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: kotlinx.coroutines.CancellationException) { throw error
    } catch (error: Exception) {
        Result.failure(ApiException("API_ERROR", error.toUserMessage(gson)))
    }
}
