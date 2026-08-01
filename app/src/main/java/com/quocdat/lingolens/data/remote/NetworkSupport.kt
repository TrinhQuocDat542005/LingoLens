package com.quocdat.lingolens.data.remote

import com.google.gson.Gson
import com.quocdat.lingolens.data.local.SessionStorage
import com.quocdat.lingolens.data.remote.api.AuthApi
import com.quocdat.lingolens.data.remote.dto.ApiErrorDto
import com.quocdat.lingolens.data.remote.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException

class AuthInterceptor(private val storage: SessionStorage) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { storage.current()?.accessToken }
        val request = if (token.isNullOrBlank()) chain.request() else chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}

class TokenAuthenticator(private val storage: SessionStorage, private val authApi: AuthApi) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        return runBlocking {
            val session = storage.current() ?: return@runBlocking null
            try {
                val refreshed = authApi.refresh(RefreshRequest(session.refreshToken)).data ?: return@runBlocking null
                storage.save(refreshed)
                response.request.newBuilder().header("Authorization", "Bearer ${refreshed.accessToken}").build()
            } catch (_: Exception) {
                storage.clear()
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }
}

class ApiException(val code: String, override val message: String) : IOException(message)

fun Throwable.toUserMessage(gson: Gson): String = when (this) {
    is ApiException -> message
    is HttpException -> response()?.errorBody()?.string()?.let {
        runCatching { gson.fromJson(it, ApiErrorDto::class.java).message }.getOrNull()
    } ?: "Máy chủ từ chối yêu cầu."
    is IOException -> "Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại."
    else -> "Đã xảy ra lỗi. Vui lòng thử lại."
}
