package com.quocdat.lingolens.data.remote.api

import com.quocdat.lingolens.data.remote.dto.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RecognitionApi {
    @POST("api/v1/recognitions")
    suspend fun create(@Body request: CreateRecognitionRequest): ApiEnvelope<RecognitionHistoryDto>

    @GET("api/v1/recognitions")
    suspend fun history(@Query("page") page: Int = 0, @Query("size") size: Int = 20): ApiEnvelope<RecognitionPageDto>

    @POST("api/v1/recognitions/{historyId}/reports")
    suspend fun report(@Path("historyId") historyId: Long, @Body request: CreateRecognitionReportRequest): ApiEnvelope<RecognitionReportDto>
}
