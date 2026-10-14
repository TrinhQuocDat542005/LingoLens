package com.quocdat.lingolens.analytics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class ConsentRequest(val optIn:Boolean,val consentVersion:String?=null,@field:Min(0) val expectedRevision:Long)
data class ConsentState(val optIn:Boolean,val consentVersion:String?,val consentAt:Instant?,val revision:Long)
data class BatchRequest(@field:Size(min=1,max=50) val events:List<JsonNode>)
data class AnalyticsItemResult(val id:String?,val status:String)
data class BatchResult(val results:List<AnalyticsItemResult>)
data class RetentionReport(val observedAccounts:Long,val eligibleD1:Long,val returnedD1:Long,val eligibleD7:Long,val returnedD7:Long,val windowDays:Int=90) {
    val d1Rate:Double? get()=if(eligibleD1==0L)null else returnedD1.toDouble()/eligibleD1
    val d7Rate:Double? get()=if(eligibleD7==0L)null else returnedD7.toDouble()/eligibleD7
}
class AnalyticsRequestException(val status:HttpStatus,val code:String,message:String):RuntimeException(message)
class AnalyticsItemException(val status:String):IllegalArgumentException(status)
data class AnalyticsEvent(val id:UUID,val eventName:String,val sessionId:UUID?,val attemptId:UUID?,val occurredAt:Instant,
    val appVersion:String,val timezone:String,val properties:Map<String,Any>,val consentRevision:Long) {
    fun hash(mapper:ObjectMapper):String {
        // Source dedupe ignores transport UUID; all other frozen fields participate.
        val canonical=listOf(eventName,sessionId?.toString(),attemptId?.toString(),occurredAt.toString(),appVersion,timezone,properties.toSortedMap(),consentRevision)
        return MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical)).joinToString(""){"%02x".format(it)}
    }
}
