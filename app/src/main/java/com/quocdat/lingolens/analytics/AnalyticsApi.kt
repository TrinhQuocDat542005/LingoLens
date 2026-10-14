package com.quocdat.lingolens.analytics

import com.google.gson.JsonObject
import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.*
import java.io.IOException

data class AnalyticsConsentReply(val optIn:Boolean,val consentVersion:String?,val consentAt:String?,val revision:Long)
data class AnalyticsConsentRequest(val optIn:Boolean,val consentVersion:String?,val expectedRevision:Long)
data class AnalyticsEventDto(val id:String,val eventName:String,val sessionId:String?,val attemptId:String?,val occurredAt:String,
    val appVersion:String,val timezone:String,val properties:JsonObject,val consentRevision:Long)
data class AnalyticsBatchRequest(val events:List<AnalyticsEventDto>)
data class AnalyticsAck(val id:String?,val status:String)
data class AnalyticsBatchReply(val results:List<AnalyticsAck>)
interface AnalyticsGateway {
    suspend fun consent(id:Long):AnalyticsConsentReply
    suspend fun update(id:Long,input:AnalyticsConsentRequest):AnalyticsConsentReply
    suspend fun batch(id:Long,input:AnalyticsBatchRequest):AnalyticsBatchReply
}
interface AnalyticsApi {
    @GET("api/v1/analytics/consent") suspend fun consent(@Header("X-LingoLens-Account") id:Long):Response<ApiEnvelope<AnalyticsConsentReply>>
    @PUT("api/v1/analytics/consent") suspend fun update(@Header("X-LingoLens-Account") id:Long,@Body input:AnalyticsConsentRequest):Response<ApiEnvelope<AnalyticsConsentReply>>
    @POST("api/v1/analytics/events/batch") suspend fun batch(@Header("X-LingoLens-Account") id:Long,@Body input:AnalyticsBatchRequest):Response<ApiEnvelope<AnalyticsBatchReply>>
}
class RemoteAnalyticsGateway(private val api:AnalyticsApi):AnalyticsGateway {
    override suspend fun consent(id:Long)=body(api.consent(id))
    override suspend fun update(id:Long,input:AnalyticsConsentRequest)=body(api.update(id,input))
    override suspend fun batch(id:Long,input:AnalyticsBatchRequest)=body(api.batch(id,input))
    private fun <T> body(response:Response<ApiEnvelope<T>>):T {
        if(!response.isSuccessful)throw HttpException(response)
        return response.body()?.takeIf{it.success}?.data ?: throw IOException("Invalid analytics response")
    }
}
