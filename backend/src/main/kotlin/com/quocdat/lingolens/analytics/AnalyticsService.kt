package com.quocdat.lingolens.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Service
class AnalyticsService(private val jdbc:JdbcTemplate,private val mapper:ObjectMapper) {
    @Transactional(readOnly=true)
    fun consent(account:Long):ConsentState {
        val row=jdbc.queryForMap("SELECT analytics_opt_in,analytics_consent_version,analytics_consent_at,analytics_consent_revision FROM learner_settings WHERE user_id=?",account)
        return ConsentState(row["analytics_opt_in"] as Boolean,row["analytics_consent_version"] as String?,
            when(val at=row["analytics_consent_at"]){is Timestamp->at.toInstant();is java.time.OffsetDateTime->at.toInstant();else->null},(row["analytics_consent_revision"] as Number).toLong())
    }
    @Transactional
    fun updateConsent(account:Long,request:ConsentRequest):ConsentState {
        require(request.expectedRevision>=0 && (!request.optIn || request.consentVersion=="analytics-v1")){"Unsupported analytics consent"}
        lock(account);val old=consent(account)
        if(old.revision!=request.expectedRevision)throw AnalyticsRequestException(HttpStatus.CONFLICT,"CONSENT_REVISION_CONFLICT","Reload analytics consent before updating")
        if(old.optIn==request.optIn && (!request.optIn || old.consentVersion==request.consentVersion))return old
        jdbc.update("UPDATE learner_settings SET analytics_opt_in=?,analytics_consent_version=?,analytics_consent_at=?,analytics_consent_revision=analytics_consent_revision+1 WHERE user_id=?",
            request.optIn,if(request.optIn)request.consentVersion else old.consentVersion,if(request.optIn)Timestamp.from(Instant.now()) else old.consentAt?.let(Timestamp::from),account)
        return consent(account)
    }
    @Transactional
    fun batch(account:Long,request:BatchRequest):BatchResult {
        require(request.events.size in 1..50 && mapper.writeValueAsBytes(request).size<=128*1024){"Analytics batch exceeds limits"}
        val now=Instant.now()
        val parsed=request.events.map{node->try{AnalyticsValidation.parse(node,now,mapper) to null}catch(error:AnalyticsItemException){null to error.status}}
        lock(account);val state=consent(account)
        return BatchResult(parsed.mapIndexed{index,(event,error)->
            val status=when{error!=null->error;!state.optIn->"DROPPED_OPT_OUT";event!!.consentRevision!=state.revision->"DROPPED_STALE_CONSENT";else->insert(account,event,now)}
            AnalyticsItemResult(request.events[index]["id"]?.takeIf{it.isTextual && it.asText().length<=36}?.asText(),status)
        })
    }
    private fun insert(account:Long,event:AnalyticsEvent,received:Instant):String {
        val hash=event.hash(mapper)
        val inserted=jdbc.update("INSERT INTO analytics_events(id,user_id,event_name,session_id,attempt_id,occurred_at,received_at,app_version,timezone,properties,payload_hash,consent_revision) VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS JSONB),?,?) ON CONFLICT DO NOTHING",
            event.id,account,event.eventName,event.sessionId,event.attemptId,Timestamp.from(event.occurredAt),Timestamp.from(received),event.appVersion,event.timezone,mapper.writeValueAsString(event.properties),hash,event.consentRevision)
        if(inserted==1)return "ACCEPTED"
        val byId=jdbc.queryForList("SELECT user_id,payload_hash FROM analytics_events WHERE id=?",event.id).firstOrNull()
        if(byId!=null)return if((byId["user_id"] as Number).toLong()==account && byId["payload_hash"]==hash)"DUPLICATE" else "REJECTED_UUID_CONFLICT"
        val source=if(event.eventName.startsWith("lesson"))"attempt_id" else "session_id"
        val old=jdbc.queryForList("SELECT payload_hash FROM analytics_events WHERE user_id=? AND event_name=? AND $source=?",account,event.eventName,event.attemptId ?: event.sessionId).firstOrNull()
        return if(old?.get("payload_hash")==hash)"DUPLICATE" else "REJECTED_UUID_CONFLICT"
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun recordPassed(account:Long,attempt:UUID) {
        lock(account);val state=consent(account);if(!state.optIn)return
        val row=jdbc.queryForList("SELECT a.client_completed_at,a.submitted_offline,r.lesson_id,a.lesson_revision_id FROM lesson_attempts a JOIN lesson_revisions r ON r.id=a.lesson_revision_id WHERE a.id=? AND a.user_id=? AND a.mode='LESSON' AND a.confirmation_status='CONFIRMED' AND a.passed",attempt,account).firstOrNull() ?: return
        val now=Instant.now()
        // Confirmation time, not the untrusted device clock, defines this server event.
        val event=AnalyticsEvent(UUID.nameUUIDFromBytes("lesson_passed:$attempt".toByteArray(Charsets.UTF_8)),"lesson_passed",null,attempt,now,"unknown","UTC",
            mapOf("lessonId" to row["lesson_id"].toString(),"revisionId" to row["lesson_revision_id"].toString(),"offline" to row["submitted_offline"]!!),state.revision)
        insert(account,event,now)
    }
    @Transactional
    fun prune(now:Instant=Instant.now()):Int=jdbc.update("DELETE FROM analytics_events WHERE received_at<=?",Timestamp.from(now.minusSeconds(AnalyticsValidation.RETENTION_SECONDS)))
    @Transactional(readOnly=true)
    fun retention(now:Instant=Instant.now()):RetentionReport {
        val row=jdbc.queryForMap("""
            WITH observed AS (
             SELECT user_id,occurred_at,(occurred_at AT TIME ZONE timezone)::date AS event_day
             FROM analytics_events WHERE event_name='app_open' AND received_at>? AND received_at<=? AND occurred_at>? AND occurred_at<=?
            ), first_seen AS (
             SELECT DISTINCT ON(user_id) user_id,occurred_at first_at,event_day first_day FROM observed ORDER BY user_id,occurred_at,event_day
            ), returns AS (
             SELECT f.*,
              EXISTS(SELECT 1 FROM observed o WHERE o.user_id=f.user_id AND o.event_day=f.first_day+1) d1,
              EXISTS(SELECT 1 FROM observed o WHERE o.user_id=f.user_id AND o.event_day=f.first_day+7) d7 FROM first_seen f
            ) SELECT count(*) observed,
             count(*) FILTER(WHERE first_at<=?) eligible_d1,count(*) FILTER(WHERE first_at<=? AND d1) returned_d1,
             count(*) FILTER(WHERE first_at<=?) eligible_d7,count(*) FILTER(WHERE first_at<=? AND d7) returned_d7 FROM returns
        """.trimIndent(),Timestamp.from(now.minusSeconds(AnalyticsValidation.RETENTION_SECONDS)),Timestamp.from(now),Timestamp.from(now.minusSeconds(AnalyticsValidation.RETENTION_SECONDS)),Timestamp.from(now),
            Timestamp.from(now.minusSeconds(2L*86400)),Timestamp.from(now.minusSeconds(2L*86400)),Timestamp.from(now.minusSeconds(8L*86400)),Timestamp.from(now.minusSeconds(8L*86400)))
        fun number(key:String)=(row[key] as Number).toLong()
        return RetentionReport(number("observed"),number("eligible_d1"),number("returned_d1"),number("eligible_d7"),number("returned_d7"))
    }
    private fun lock(account:Long){jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR NO KEY UPDATE",Long::class.java,account)}
}
