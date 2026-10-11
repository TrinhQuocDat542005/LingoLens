package com.quocdat.lingolens.sync

import com.google.gson.JsonObject
import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import retrofit2.http.*

data class SyncRequest(val accountId: Long, val operationId: String, val kind: String, val payload: JsonObject)
data class SyncReceipt(val operationId: String, val remoteId: Long?)
data class CloudEntry(val word: String, val content: String?, val savedAt: Long?, val firstLearnedAt: Long?, val deleted: Boolean)
data class CloudReviewCard(val word: String, val dueAt: Long, val intervalDays: Int, val ease: Double, val repetitions: Int, val lapses: Int, val stage: String, val lastReviewedAt: Long?)
data class CloudReviewEvent(val id: String, val word: String, val grade: String, val reviewedAt: Long)
data class ReviewSnapshot(val cards: List<CloudReviewCard>, val events: List<CloudReviewEvent>)
interface SyncApi {
    @POST("api/v1/sync/operations") suspend fun apply(@Header("X-LingoLens-Account") account: Long, @Body request: SyncRequest): ApiEnvelope<SyncReceipt>
    @GET("api/v1/sync/learning") suspend fun snapshot(@Header("X-LingoLens-Account") account: Long, @Query("accountId") accountId: Long): ApiEnvelope<List<CloudEntry>>
    @GET("api/v1/sync/reviews") suspend fun reviews(@Header("X-LingoLens-Account") account: Long, @Query("accountId") accountId: Long): ApiEnvelope<ReviewSnapshot>
}
