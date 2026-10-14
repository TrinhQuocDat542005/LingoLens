package com.quocdat.lingolens.analytics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

object AnalyticsValidation {
    const val RETENTION_SECONDS=90L*86400
    private val fields=setOf("id","eventName","sessionId","attemptId","occurredAt","appVersion","timezone","properties","consentRevision")
    private val clientNames=setOf("app_open","lesson_started","lesson_completed","review_session_completed")
    fun parse(node:JsonNode,now:Instant,mapper:ObjectMapper):AnalyticsEvent {
        try {
            require(node.isObject && node.fieldNames().asSequence().all{it in fields})
            fun text(name:String):String {val value=node[name];require(value!=null && value.isTextual);return value.asText()}
            fun uuid(name:String,required:Boolean=false):UUID? {val n=node[name];if(n==null || n.isNull){require(!required);return null};val raw=text(name);val id=UUID.fromString(raw);require(id.toString()==raw.lowercase());return id}
            val name=text("eventName");require(name in clientNames)
            val id=uuid("id",true)!!;val session=uuid("sessionId",name in setOf("app_open","review_session_completed"));val attempt=uuid("attemptId",name.startsWith("lesson"))
            val occurred=Instant.parse(text("occurredAt"));require(!occurred.isAfter(now.plusSeconds(300)))
            val appVersion=text("appVersion");require(appVersion.length in 1..40 && appVersion.none{it.isISOControl()})
            val zone=text("timezone");require(zone.length<=64 && zone in ZoneId.getAvailableZoneIds())
            val generation=node["consentRevision"];require(generation!=null && generation.isIntegralNumber && generation.canConvertToLong() && generation.asLong()>=0)
            val props=node["properties"];require(props!=null && props.isObject && mapper.writeValueAsBytes(props).size<=2048)
            val values=props.fields().asSequence().associate{(key,value)->
                val parsed:Any=when(key){
                    "courseId","lessonId","revisionId"->{require(value.isTextual);val raw=value.asText();require(UUID.fromString(raw).toString()==raw.lowercase());raw.lowercase()}
                    "questionCount","correctCount","wordCount"->{require(value.isIntegralNumber && value.canConvertToInt() && value.asInt() in 0..1000);value.asInt()}
                    "offline"->{require(value.isBoolean);value.asBoolean()}
                    else->throw IllegalArgumentException("Unknown property")
                };key to parsed
            }
            if(occurred.isBefore(now.minusSeconds(RETENTION_SECONDS)))throw AnalyticsItemException("DROPPED_EXPIRED")
            return AnalyticsEvent(id,name,session,attempt,occurred,appVersion,zone,values,generation.asLong())
        }catch(error:AnalyticsItemException){throw error}
        catch(error:Exception){throw AnalyticsItemException("REJECTED_INVALID")}
    }
}
