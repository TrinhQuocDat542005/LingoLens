package com.quocdat.lingolens.analytics

import androidx.room.withTransaction
import com.google.gson.*
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.repository.ReviewProgress
import com.quocdat.lingolens.sync.SyncOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class AnalyticsRepository(private val db:LearningDatabase,private val gateway:AnalyticsGateway,
    private val identity:suspend(Long)->String?,private val clock:()->Long=System::currentTimeMillis,
    private val version:String,private val timezone:()->String={ZoneId.systemDefault().id},
    private val schedule:(Long)->Unit={},private val cancel:(Long)->Unit={}) {
    private val dao=db.analyticsDao()
    private val gson=Gson()
    private val syncMutex=Mutex()
    suspend fun awaitIdle(){syncMutex.withLock { Unit }}
    companion object {const val VERSION="analytics-v1";const val RETENTION=90L*86400000}
    fun observe(account:Long)=dao.observeConsent(account)
    suspend fun currentState(account:Long)=dao.consent(account)
    suspend fun initialize(account:Long):AnalyticsConsentState? {
        val key=identity(account) ?: return null
        return applyReply(account,gateway.consent(account),key)
    }
    suspend fun enable(account:Long):Boolean {
        val key=identity(account) ?: error("Sign in before changing consent")
        val old=dao.consent(account) ?: initialize(account) ?: return false
        check(!old.pendingOptOut){"Finish pending opt out before enabling"}
        val reply=gateway.update(account,AnalyticsConsentRequest(true,VERSION,old.consentRevision))
        val state=applyReply(account,reply,key)
        if(state?.enabled==true){schedule(account);return true};return false
    }
    suspend fun optOut(account:Long) {
        if(identity(account)==null)return
        db.withTransaction {
            val old=dao.consent(account) ?: AnalyticsConsentState(account,updatedAt=clock())
            dao.saveConsent(old.copy(enabled=false,pendingOptOut=true,updatedAt=clock()))
            dao.purge(account)
        }
        cancel(account);schedule(account)
    }
    private suspend fun applyReply(account:Long,reply:AnalyticsConsentReply,key:String):AnalyticsConsentState? {
        require(reply.revision>=0)
        val at=reply.consentAt?.let{Instant.parse(it).toEpochMilli()}
        require(!reply.optIn || (reply.consentVersion==VERSION && at!=null && reply.revision>0))
        if(identity(account)!=key)return null
        return db.withTransaction {
            if(identity(account)!=key)return@withTransaction null
            val old=dao.consent(account)
            if(old!=null && reply.revision<old.consentRevision)throw IOException("Stale consent response")
            val pending=old?.pendingOptOut==true && reply.optIn
            val enabled=reply.optIn && !pending
            if(old==null || old.consentRevision!=reply.revision || !enabled)dao.purge(account)
            val same=old!=null && old.consentRevision==reply.revision && old.enabled==enabled && old.pendingOptOut==pending
            val state=AnalyticsConsentState(account,enabled,reply.consentVersion,reply.revision,at,pending,if(same)old!!.updatedAt else clock())
            dao.saveConsent(state);state
        }
    }
    suspend fun capture(account:Long,name:String,source:String,at:Long,properties:JsonObject=JsonObject()):Boolean {
        val key=identity(account) ?: return false
        val props=canonical(properties) ?: return false
        if(name !in setOf("app_open","lesson_started","lesson_completed","review_session_completed") ||
            at<=0 || at<clock()-RETENTION || at>clock()+300000 || version.length !in 1..40 ||
            timezone() !in ZoneId.getAvailableZoneIds() || runCatching{UUID.fromString(source)}.isFailure)return false
        val id=UUID.nameUUIDFromBytes("$account:$name:$source".toByteArray(Charsets.UTF_8)).toString()
        val inserted=db.withTransaction {
            val state=dao.consent(account) ?: return@withTransaction false
            if(!state.enabled || state.pendingOptOut || state.consentVersion!=VERSION || state.consentRevision<=0 ||
                at<maxOf(state.updatedAt,state.consentAt ?: state.updatedAt) || identity(account)!=key)return@withTransaction false
            if(dao.event(account,id)!=null)return@withTransaction false
            val session=if(name.startsWith("lesson"))null else source
            val attempt=if(name.startsWith("lesson"))source else null
            val candidate=AnalyticsOutbox(account,id,name,session,attempt,at,timezone(),version,gson.toJson(props),"",state.consentRevision,"PENDING",0,clock())
            dao.enqueue(candidate.copy(payloadHash=hash(wire(candidate))));true
        }
        if(inserted)schedule(account)
        return inserted
    }
    private fun canonical(properties:JsonObject):JsonObject?=runCatching {
        require(gson.toJson(properties).toByteArray(Charsets.UTF_8).size<=2048)
        JsonObject().apply {properties.entrySet().sortedBy{it.key}.forEach{(key,value)->
            require(value.isJsonPrimitive)
            when(key){
                "courseId","lessonId","revisionId"->{require(value.asJsonPrimitive.isString);UUID.fromString(value.asString)}
                "questionCount","correctCount","wordCount"->{require(value.asJsonPrimitive.isNumber && value.asString.matches(Regex("\\d{1,4}")) && value.asInt in 0..1000)}
                "offline"->require(value.asJsonPrimitive.isBoolean)
                else->error("Disallowed analytics property")
            };add(key,value.deepCopy())
        }}
    }.getOrNull()
    private fun wire(row:AnalyticsOutbox)=AnalyticsEventDto(row.eventId,row.eventName,row.sessionId,row.attemptId,
        Instant.ofEpochMilli(row.occurredAt).toString(),row.appVersion,row.timezone,JsonParser.parseString(row.propertiesJson).asJsonObject,row.consentRevision)
    private fun hash(dto:AnalyticsEventDto)=MessageDigest.getInstance("SHA-256").digest(gson.toJson(dto).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    suspend fun prune(account:Long){dao.prune(account,clock()-RETENTION)}
    suspend fun sync(account:Long):SyncOutcome=syncMutex.withLock {
        val key=identity(account) ?: return@withLock SyncOutcome.PAUSED
        prune(account)
        var state=dao.consent(account) ?: return@withLock SyncOutcome.COMPLETE
        if(!state.enabled && !state.pendingOptOut)return@withLock SyncOutcome.COMPLETE
        try{
            state=applyReply(account,gateway.consent(account),key) ?: return@withLock SyncOutcome.PAUSED
            if(state.pendingOptOut){
                state=applyReply(account,gateway.update(account,AnalyticsConsentRequest(false,null,state.consentRevision)),key) ?: return@withLock SyncOutcome.PAUSED
                if(state.pendingOptOut)return@withLock SyncOutcome.RETRY
            }
            if(!state.enabled)return@withLock SyncOutcome.COMPLETE
        }catch(error:CancellationException){throw error
        }catch(error:Exception){
            if(identity(account)!=key)return@withLock SyncOutcome.PAUSED
            if(error is HttpException && error.code()==409){
                try{applyReply(account,gateway.consent(account),key)}catch(cancel:CancellationException){throw cancel}catch(_:Exception){}
                return@withLock SyncOutcome.RETRY
            }
            return@withLock classify(account,key,error,dao.events(account).filter{it.state=="PENDING"})
        }
        var blocked=dao.events(account).any{it.state=="BLOCKED"};var retry=false
        for(chunk in dao.events(account).filter{it.state=="PENDING"}.chunked(50)){
            if(identity(account)!=key)return@withLock SyncOutcome.PAUSED
            val current=dao.consent(account) ?: return@withLock SyncOutcome.COMPLETE
            if(!current.enabled || current.pendingOptOut)return@withLock SyncOutcome.COMPLETE
            val valid=mutableListOf<AnalyticsOutbox>()
            for(row in chunk){
                if(row.consentRevision!=current.consentRevision){dao.remove(account,row.eventId);continue}
                if(runCatching{hash(wire(row))==row.payloadHash}.getOrDefault(false))valid.add(row)
                else{dao.update(row.copy(state="BLOCKED",lastError="ANALYTICS_PAYLOAD_CORRUPT"));blocked=true}
            }
            if(valid.isEmpty())continue
            try{
                val request=AnalyticsBatchRequest(valid.map(::wire))
                require(gson.toJson(request).toByteArray(Charsets.UTF_8).size<=128*1024)
                val reply=gateway.batch(account,request)
                if(identity(account)!=key)return@withLock SyncOutcome.PAUSED
                val expected=valid.map{it.eventId}.toSet()
                val allowed=setOf("ACCEPTED","DUPLICATE","DROPPED_EXPIRED","DROPPED_OPT_OUT","DROPPED_STALE_CONSENT","REJECTED_INVALID","REJECTED_UUID_CONFLICT")
                if(reply.results.size!=expected.size || reply.results.map{it.id}.toSet()!=expected || reply.results.any{it.status !in allowed})throw IOException("Invalid analytics acknowledgement")
                db.withTransaction {
                    val latest=dao.consent(account)
                    if(identity(account)!=key || latest?.enabled!=true || latest.pendingOptOut || latest.consentRevision!=current.consentRevision)return@withTransaction
                    for(ack in reply.results){
                        val row=dao.event(account,ack.id!!) ?: continue
                        if(row.payloadHash!=valid.first{it.eventId==ack.id}.payloadHash)continue
                        when(ack.status){
                            "ACCEPTED","DUPLICATE"->dao.update(row.copy(state="ACKNOWLEDGED",lastError=null))
                            "REJECTED_INVALID","REJECTED_UUID_CONFLICT"->{dao.update(row.copy(state="BLOCKED",lastError=ack.status));blocked=true}
                            else->dao.remove(account,row.eventId)
                        }
                    }
                }
            }catch(error:CancellationException){throw error
            }catch(error:Exception){
                when(classify(account,key,error,valid)){
                    SyncOutcome.PAUSED->return@withLock SyncOutcome.PAUSED
                    SyncOutcome.RETRY->retry=true
                    SyncOutcome.BLOCKED->blocked=true
                    else->Unit
                }
            }
        }
        when{identity(account)!=key->SyncOutcome.PAUSED;retry->SyncOutcome.RETRY;blocked->SyncOutcome.BLOCKED;else->SyncOutcome.COMPLETE}
    }
    private suspend fun classify(account:Long,key:String,error:Exception,rows:List<AnalyticsOutbox>):SyncOutcome {
        if(identity(account)!=key || (error is HttpException && error.code() in setOf(401,403)))return SyncOutcome.PAUSED
        val transient=error is IOException || (error is HttpException && (error.code()>=500 || error.code() in setOf(408,429)))
        db.withTransaction {if(identity(account)==key){for(row in rows){val present=dao.event(account,row.eventId) ?: continue
            if(present.state=="PENDING")dao.update(present.copy(state=if(transient)"PENDING" else "BLOCKED",retryCount=present.retryCount+1,lastError=if(transient)"ANALYTICS_RETRY" else "ANALYTICS_REJECTED"))
        }}}
        return if(transient)SyncOutcome.RETRY else SyncOutcome.BLOCKED
    }
    suspend fun onLessonStarted(session:LessonSession){
        if(session.mode!="LESSON")return
        capture(session.accountId,"lesson_started",session.id,session.startedAt,JsonObject().apply{addProperty("lessonId",session.lessonId);addProperty("revisionId",session.revisionId)})
    }
    suspend fun onLessonCompleted(account:Long,id:String){
        val session=db.lessonDao().session(account,id) ?: return
        if(session.mode!="LESSON" || session.frozenPayloadJson==null)return
        val payload=JsonParser.parseString(session.frozenPayloadJson).asJsonObject
        capture(account,"lesson_completed",session.id,payload["clientCompletedAt"].asLong,JsonObject().apply{
            addProperty("lessonId",session.lessonId);addProperty("revisionId",session.revisionId)
            addProperty("questionCount",payload["answers"].asJsonArray.size());addProperty("offline",payload["submittedOffline"].asBoolean)
        })
    }
    suspend fun onReviewFinished(progress:ReviewProgress){
        if(progress.session.finishedAt==null || progress.answers.isEmpty())return
        capture(progress.session.accountId,"review_session_completed",progress.session.id,progress.session.finishedAt,
            JsonObject().apply{addProperty("wordCount",progress.answers.size)})
    }
    suspend fun recover(account:Long){
        val state=dao.consent(account) ?: return
        if(!state.enabled || state.pendingOptOut || identity(account)==null)return
        for(session in db.lessonDao().sessions(account)){
            bestEffort{onLessonStarted(session);onLessonCompleted(account,session.id)}
        }
        for(session in db.reviewDao().finishedSessions(account,state.updatedAt)){
            bestEffort{onReviewFinished(ReviewProgress(session,0,null,null,db.reviewDao().sessionEvents(account,session.id)))}
        }
    }
    suspend fun bestEffort(action:suspend()->Unit){try{action()}catch(error:CancellationException){throw error}catch(_:Exception){}}
}
