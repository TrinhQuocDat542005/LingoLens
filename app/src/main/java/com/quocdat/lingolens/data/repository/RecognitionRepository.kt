package com.quocdat.lingolens.data.repository

import com.google.gson.Gson
import com.quocdat.lingolens.data.remote.api.RecognitionApi
import com.quocdat.lingolens.data.remote.dto.*
import com.quocdat.lingolens.data.remote.toUserMessage

class RecognitionRepository(private val api: RecognitionApi, private val gson: Gson) {
    suspend fun save(label: String, confidence: Float, engine: String): Result<RecognitionHistoryDto> =
        runCatching { requireNotNull(api.create(CreateRecognitionRequest(label, confidence, engine)).data) }
            .mapError()

    suspend fun history(): Result<List<RecognitionHistoryDto>> =
        runCatching { requireNotNull(api.history().data).content }.mapError()

    suspend fun report(historyId: Long, expectedLabel: String, note: String?): Result<RecognitionReportDto> =
        runCatching { requireNotNull(api.report(historyId, CreateRecognitionReportRequest(expectedLabel, note)).data) }
            .mapError()

    private fun <T> Result<T>.mapError(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(IllegalStateException(it.toUserMessage(gson), it)) }
    )
}
