package com.quocdat.lingolens.data.remote.api

import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import com.quocdat.lingolens.data.remote.dto.UpdateProfileRequest
import com.quocdat.lingolens.data.remote.dto.UserProfileDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface UserApi {
    @GET("api/v1/users/me")
    suspend fun getMe(): ApiEnvelope<UserProfileDto>

    @PUT("api/v1/users/me")
    suspend fun updateMe(@Body request: UpdateProfileRequest): ApiEnvelope<UserProfileDto>
}
