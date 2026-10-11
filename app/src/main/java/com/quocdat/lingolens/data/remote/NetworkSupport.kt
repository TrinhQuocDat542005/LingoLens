package com.quocdat.lingolens.data.remote

import com.google.gson.Gson
import com.quocdat.lingolens.data.local.CredentialStore
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

data class RequestAccount(val email: String)

class AuthInterceptor(private val storage: CredentialStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val session = runBlocking {
            val expected = chain.request().header("X-LingoLens-Account")?.toLongOrNull()
            if (expected != null && storage.cachedProfile()?.id != expected) throw IOException("Sync account changed")
            storage.current()
        }
        val request = if (session == null || session.accessToken.isBlank()) chain.request() else chain.request().newBuilder()
            .header("Authorization", "Bearer ${session.accessToken}")
            .tag(RequestAccount::class.java, RequestAccount(session.email))
            .build()
        return chain.proceed(request)
    }
}

class TokenAuthenticator(private val storage: CredentialStore, private val authApi: AuthApi) : Authenticator {
    private val refreshLock = Any()
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        return synchronized(refreshLock) { runBlocking {
            val session = storage.current() ?: return@runBlocking null
            val originalAccount = response.request.tag(RequestAccount::class.java)
            if (originalAccount != null && originalAccount.email != session.email) return@runBlocking null
            val expected = response.request.header("X-LingoLens-Account")?.toLongOrNull()
            if (expected != null && storage.cachedProfile()?.id != expected) return@runBlocking null
            if (response.request.header("Authorization") != "Bearer ${session.accessToken}") {
                return@runBlocking response.request.newBuilder().header("Authorization", "Bearer ${session.accessToken}").build()
            }
            try {
                val refreshed = authApi.refresh(RefreshRequest(session.refreshToken)).data ?: return@runBlocking null
                if (!storage.saveRefreshed(session, refreshed)) return@runBlocking null
                response.request.newBuilder().header("Authorization", "Bearer ${refreshed.accessToken}").build()
            } catch (ex: Exception) {
                if (ex is HttpException && ex.code() in listOf(401, 403)) storage.clearIfCurrent(session)
                null
            }
        } }
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
