package com.quocdat.lingolens.data.remote.api

import com.quocdat.lingolens.data.remote.dto.*
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiEnvelope<UserProfileDto>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiEnvelope<TokenDto>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): ApiEnvelope<TokenDto>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): ApiEnvelope<Unit>

    @POST("api/v1/auth/logout-all")
    suspend fun logoutAll(): ApiEnvelope<Unit>
}
