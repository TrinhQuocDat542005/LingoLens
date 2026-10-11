package com.quocdat.lingolens.data.repository

import com.google.gson.Gson
import com.quocdat.lingolens.data.remote.api.RecognitionApi
import com.quocdat.lingolens.data.remote.dto.*
import com.quocdat.lingolens.data.remote.toUserMessage

class RecognitionRepository(private val api: RecognitionApi, private val gson: Gson, private val db: com.quocdat.lingolens.data.local.LearningDatabase, private val schedule: (Long) -> Unit) {
    suspend fun save(accountId: Long, label: String, confidence: Float, engine: String): Result<String> = queue(accountId, "RECOGNITION", CreateRecognitionRequest(label, confidence, engine))

    suspend fun history(): Result<List<RecognitionHistoryDto>> =
        runCatching { requireNotNull(api.history().data).content }.mapError()

    suspend fun report(accountId: Long, operationId: String, expectedLabel: String, note: String?): Result<String> = queue(accountId, "REPORT",
        mapOf("recognitionOperationId" to operationId, "expectedLabel" to expectedLabel, "note" to note))

    private suspend fun queue(accountId: Long, kind: String, payload: Any): Result<String> = try {
        val id = java.util.UUID.randomUUID().toString()
        db.syncDao().enqueue(com.quocdat.lingolens.data.local.OutboxOperation(operationId = id, accountId = accountId, kind = kind, payload = gson.toJson(payload)))
        schedule(accountId)
        Result.success(id)
    } catch (ex: kotlinx.coroutines.CancellationException) { throw ex
    } catch (ex: Exception) { Result.failure(IllegalStateException("Không lưu được thao tác trên thiết bị.", ex)) }

    private fun <T> Result<T>.mapError(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { if (it is kotlinx.coroutines.CancellationException) throw it; Result.failure(IllegalStateException(it.toUserMessage(gson), it)) }
    )
}
