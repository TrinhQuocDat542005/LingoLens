package com.quocdat.lingolens.analytics

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AnalyticsValidationTests {
    private val mapper=jacksonObjectMapper()
    private val now=Instant.parse("2026-10-13T00:00:00Z")
    private fun event()=mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(mapOf(
        "id" to UUID.randomUUID().toString(),"eventName" to "app_open","sessionId" to UUID.randomUUID().toString(),
        "occurredAt" to now.toString(),"appVersion" to "1.0","timezone" to "America/New_York","properties" to emptyMap<String,String>(),"consentRevision" to 1))
    @Test fun `strict allowlist rejects personal fields and unknown properties`() {
        for(field in listOf("userId","email","token","answer","image")) {
            val e=event().put(field,"private");assertEquals("REJECTED_INVALID",assertThrows(AnalyticsItemException::class.java){AnalyticsValidation.parse(e,now,mapper)}.status)
        }
        val e=event();e.putObject("properties").put("answer","private")
        assertThrows(AnalyticsItemException::class.java){AnalyticsValidation.parse(e,now,mapper)}
    }
    @Test fun `only client event names and required source UUIDs are accepted`() {
        for(name in listOf("lesson_started","lesson_completed")){
            val e=event().put("eventName",name).put("attemptId",UUID.randomUUID().toString());assertEquals(name,AnalyticsValidation.parse(e,now,mapper).eventName)
            e.remove("attemptId");assertThrows(AnalyticsItemException::class.java){AnalyticsValidation.parse(e,now,mapper)}
        }
        val review=event().put("eventName","review_session_completed");assertEquals("review_session_completed",AnalyticsValidation.parse(review,now,mapper).eventName)
        review.remove("sessionId");assertThrows(AnalyticsItemException::class.java){AnalyticsValidation.parse(review,now,mapper)}
        assertThrows(AnalyticsItemException::class.java){AnalyticsValidation.parse(event().put("eventName","lesson_passed"),now,mapper)}
    }
    @Test fun `clock boundaries are exact and epoch or invalid timezone is rejected`() {
        val cutoff=now.minusSeconds(90L*86400)
        assertNotNull(AnalyticsValidation.parse(event().put("occurredAt",cutoff.toString()),now,mapper))
        assertEquals("DROPPED_EXPIRED",assertThrows(AnalyticsItemException::class.java){AnalyticsValidation.parse(event().put("occurredAt",cutoff.minusNanos(1).toString()),now,mapper)}.status)
        assertNotNull(AnalyticsValidation.parse(event().put("occurredAt",now.plusSeconds(300).toString()),now,mapper))
        for(e in listOf(event().put("occurredAt",now.plusSeconds(301).toString()),event().put("occurredAt",1000),event().put("timezone","+07:00"),event().put("timezone","Unknown/Zone"))){
            assertThrows(AnalyticsItemException::class.java){AnalyticsValidation.parse(e,now,mapper)}
        }
    }
    @Test fun `canonical hash ignores key order but preserves payload including consent generation`() {
        val e=event();e.putObject("properties").put("offline",true).put("correctCount",7)
        val first=AnalyticsValidation.parse(e,now,mapper)
        val reordered=e.deepCopy();reordered.putObject("properties").put("correctCount",7).put("offline",true)
        assertEquals(first.hash(mapper),AnalyticsValidation.parse(reordered,now,mapper).hash(mapper))
        assertNotEquals(first.hash(mapper),AnalyticsValidation.parse(e.put("consentRevision",2),now,mapper).hash(mapper))
    }
    @Test fun `metadata bounds and property types cannot become free text`() {
        for(e in listOf(event().put("appVersion",""),event().put("appVersion","x".repeat(41)),event().put("consentRevision",-1),event().put("id","bad"))){
            assertThrows(AnalyticsItemException::class.java){AnalyticsValidation.parse(e,now,mapper)}
        }
        for(value in listOf(-1,1001)){val e=event();e.putObject("properties").put("wordCount",value);assertThrows(AnalyticsItemException::class.java){AnalyticsValidation.parse(e,now,mapper)}}
        val e=event();e.putObject("properties").put("offline","true");assertThrows(AnalyticsItemException::class.java){AnalyticsValidation.parse(e,now,mapper)}
    }
}
