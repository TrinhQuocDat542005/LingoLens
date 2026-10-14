package com.quocdat.lingolens.analytics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class AnalyticsRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val name="analytics-mobile.db"
    private lateinit var db:LearningDatabase
    private lateinit var repo:AnalyticsRepository
    private var account=1L
    private var generation="login-1"
    private var now=2_000L
    private val requests=mutableListOf<Pair<Long,String>>()
    private val batches=mutableListOf<List<AnalyticsEventDto>>()
    private val server=mutableMapOf<Long,AnalyticsConsentReply>()
    private val received=mutableSetOf<String>()
    private var status=0
    private var conflict=false
    private var loseResponse=false
    private var badAck=false
    private var afterUpdate:suspend()->Unit={}
    private var afterBatch:suspend()->Unit={}
    private var schedules=0
    private var cancels=0
    private val gateway=object:AnalyticsGateway {
        override suspend fun consent(id:Long):AnalyticsConsentReply {assertEquals(account,id);requests.add(id to "GET");return server[id] ?: AnalyticsConsentReply(false,null,null,0)}
        override suspend fun update(id:Long,input:AnalyticsConsentRequest):AnalyticsConsentReply {
            assertEquals(account,id);requests.add(id to "PUT");failIfConfigured()
            if(conflict){conflict=false;throw HttpException(Response.error<Any>(409,"{}".toResponseBody()))}
            val prior=server[id] ?: AnalyticsConsentReply(false,null,null,0)
            assertEquals(prior.revision,input.expectedRevision)
            val reply=if(prior.optIn==input.optIn)prior else AnalyticsConsentReply(input.optIn,if(input.optIn)"analytics-v1" else prior.consentVersion,
                if(input.optIn)Instant.ofEpochMilli(now).toString() else prior.consentAt,prior.revision+1)
            server[id]=reply;afterUpdate();return reply
        }
        override suspend fun batch(id:Long,input:AnalyticsBatchRequest):AnalyticsBatchReply {
            assertEquals(account,id);requests.add(id to "BATCH");failIfConfigured();batches.add(input.events)
            val results=input.events.map{event->AnalyticsAck(event.id,if(event.properties["wordCount"]?.asInt==99)"REJECTED_INVALID" else if(received.add(event.id))"ACCEPTED" else "DUPLICATE")}
            afterBatch()
            if(loseResponse){loseResponse=false;throw java.io.IOException("Response lost")}
            return AnalyticsBatchReply(if(badAck)emptyList() else results)
        }
    }
    private fun failIfConfigured(){if(status>0)throw HttpException(Response.error<Any>(status,"{\"code\":\"TEST\"}".toResponseBody()))}
    private fun open(){db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
        repo=AnalyticsRepository(db,gateway,identity={id->if(id==account)generation else null},clock={now},version="1.0",timezone={"UTC"},schedule={schedules++},cancel={cancels++})}
    @Before fun setup(){context.deleteDatabase(name);open()}
    @After fun cleanup(){db.close();context.deleteDatabase(name)}
    private suspend fun enabled(){assertTrue(repo.enable(1))}
    private suspend fun event(source:String=UUID.randomUUID().toString(),at:Long=now,props:JsonObject=JsonObject())=repo.capture(1,"app_open",source,at,props)
    @Test fun `prompt seen flag never grants consent and removing it cannot enable telemetry`()=runBlocking {
        val flags=context.getSharedPreferences("analytics_ui",Context.MODE_PRIVATE)
        flags.edit().clear().apply()
        val prompt=AnalyticsPromptPreferences(context)
        repo.initialize(1)
        assertFalse(prompt.seen(1));assertFalse(event())
        prompt.rememberChoice(1)
        assertTrue(prompt.seen(1));assertFalse(db.analyticsDao().consent(1)!!.enabled)
        assertFalse(event());assertTrue(db.analyticsDao().events(1).isEmpty())
        afterUpdate={
            flags.edit().clear().apply()
            assertFalse(prompt.seen(1));assertFalse(db.analyticsDao().consent(1)!!.enabled)
            assertFalse(event()) // Response has not been acknowledged into Room yet.
        }
        assertTrue(repo.enable(1));assertTrue(db.analyticsDao().consent(1)!!.enabled)
        assertFalse(prompt.seen(1));assertTrue(event())
        afterUpdate={};repo.optOut(1);repo.sync(1)
        prompt.rememberChoice(1);flags.edit().clear().apply()
        assertFalse(prompt.seen(1));assertFalse(db.analyticsDao().consent(1)!!.enabled)
        assertFalse(event());assertTrue(db.analyticsDao().events(1).isEmpty())
        assertTrue(batches.isEmpty())
    }
    @Test fun `default disabled failed opt in and pre ack activity never create telemetry`()=runBlocking {
        assertFalse(event());assertTrue(db.analyticsDao().events(1).isEmpty())
        status=500;try{repo.enable(1);fail("Expected transport error")}catch(_:HttpException){}
        assertFalse(db.analyticsDao().consent(1)?.enabled==true);assertFalse(event());assertTrue(batches.isEmpty())
        status=0;val before=now;now+=1000;enabled();assertFalse(event(at=before));assertTrue(event());assertEquals(1,db.analyticsDao().events(1).size)
    }
    @Test fun `opt out purges every analytics state only and persists pending across reopen`()=runBlocking {
        enabled();event();db.analyticsDao().saveConsent(AnalyticsConsentState(2,true,"analytics-v1",1,now,false,now))
        db.analyticsDao().enqueue(db.analyticsDao().events(1).single().copy(accountId=2))
        db.learningDao().save(SavedWordEntity(1,"book","{}",now))
        db.syncDao().enqueue(OutboxOperation(operationId="legacy",accountId=1,kind="SAVE_WORD",payload="{}",state="BLOCKED"))
        status=500;repo.optOut(1);assertFalse(db.analyticsDao().consent(1)!!.enabled);assertTrue(db.analyticsDao().consent(1)!!.pendingOptOut)
        assertTrue(db.analyticsDao().events(1).isEmpty());assertEquals(1,db.analyticsDao().events(2).size)
        assertNotNull(db.learningDao().find(1,"book"));assertEquals("legacy",db.syncDao().pending(1).single().operationId);assertEquals(1,cancels)
        db.close();open();assertTrue(db.analyticsDao().consent(1)!!.pendingOptOut)
        status=0;assertEquals(SyncOutcome.COMPLETE,repo.sync(1));assertFalse(db.analyticsDao().consent(1)!!.pendingOptOut);assertFalse(server[1]!!.optIn)
    }
    @Test fun `late opt in response cannot override local opt out or a newer login`()=runBlocking {
        afterUpdate={repo.optOut(1)};assertFalse(repo.enable(1));assertFalse(db.analyticsDao().consent(1)!!.enabled);assertTrue(db.analyticsDao().consent(1)!!.pendingOptOut)
        try{repo.enable(1);fail("Pending opt out must not enable")}catch(_:IllegalStateException){}
        afterUpdate={};repo.sync(1);now+=1000
        afterUpdate={generation="login-new"};assertFalse(repo.enable(1))
        assertFalse(event())
    }
    @Test fun `same UUID frozen payload retries after lost response and database reopen`()=runBlocking {
        enabled();val source=UUID.randomUUID().toString();event(source);val before=db.analyticsDao().events(1).single();loseResponse=true
        assertEquals(SyncOutcome.RETRY,repo.sync(1));assertEquals(1,received.size)
        db.close();open();assertEquals(SyncOutcome.COMPLETE,repo.sync(1))
        val row=db.analyticsDao().events(1).single();assertEquals(before.eventId,row.eventId);assertEquals(before.payloadHash,row.payloadHash)
        assertEquals("ACKNOWLEDGED",row.state);assertEquals(batches[0],batches[1]);assertEquals(1,received.size)
        event(source);assertEquals(1,db.analyticsDao().events(1).size);repo.sync(1);assertEquals(2,batches.size)
    }
    @Test fun `logout B login and account change after response cannot dispatch or ack A`()=runBlocking {
        enabled();event();val before=db.analyticsDao().events(1).single();requests.clear()
        account=0;assertEquals(SyncOutcome.PAUSED,repo.sync(1));account=2;generation="B"
        assertEquals(SyncOutcome.PAUSED,repo.sync(1));assertEquals(SyncOutcome.COMPLETE,repo.sync(2));assertTrue(requests.isEmpty())
        assertEquals(before,db.analyticsDao().events(1).single())
        account=1;generation="login-1";afterBatch={account=2;generation="B"}
        assertEquals(SyncOutcome.PAUSED,repo.sync(1));assertEquals("PENDING",db.analyticsDao().events(1).single().state)
        assertTrue(db.analyticsDao().events(2).isEmpty())
    }
    @Test fun `batch accepts valid item beside blocked and unknown ack never loses queue`()=runBlocking {
        enabled();event(props=JsonObject().apply{addProperty("wordCount",99)});event()
        assertEquals(SyncOutcome.BLOCKED,repo.sync(1));assertEquals(setOf("BLOCKED","ACKNOWLEDGED"),db.analyticsDao().events(1).map{it.state}.toSet())
        assertEquals(SyncOutcome.BLOCKED,repo.sync(1));assertEquals(1,batches.size)
        event();badAck=true;assertEquals(SyncOutcome.RETRY,repo.sync(1));assertEquals(1,db.analyticsDao().events(1).count{it.state=="PENDING"})
    }
    @Test fun `HTTP auth pauses transient retries and permanent errors block only analytics`()=runBlocking {
        enabled();event();val id=db.analyticsDao().events(1).single().eventId
        for(code in listOf(401,403)){status=code;assertEquals(SyncOutcome.PAUSED,repo.sync(1));assertEquals("PENDING",db.analyticsDao().events(1).single().state)}
        for(code in listOf(408,429,500)){status=code;assertEquals(SyncOutcome.RETRY,repo.sync(1));assertEquals(id,db.analyticsDao().events(1).single().eventId)}
        status=400;assertEquals(SyncOutcome.BLOCKED,repo.sync(1));assertEquals("BLOCKED",db.analyticsDao().events(1).single().state)
    }
    @Test fun `remote opt out and changed revision drop stale events without backfill`()=runBlocking {
        enabled();event();server[1]=server[1]!!.copy(optIn=false,revision=2)
        assertEquals(SyncOutcome.COMPLETE,repo.sync(1));assertTrue(db.analyticsDao().events(1).isEmpty());assertFalse(db.analyticsDao().consent(1)!!.enabled)
        val old=now;now+=1000;assertTrue(repo.enable(1));assertFalse(event(at=old));assertTrue(batches.isEmpty())
    }
    @Test fun `CAS conflict refreshes pending opt out without endless loop`()=runBlocking {
        enabled();repo.optOut(1);conflict=true
        assertEquals(SyncOutcome.RETRY,repo.sync(1));assertTrue(db.analyticsDao().consent(1)!!.pendingOptOut)
        assertEquals(SyncOutcome.COMPLETE,repo.sync(1));assertFalse(db.analyticsDao().consent(1)!!.pendingOptOut)
    }
    @Test fun `retention and metadata reject expired future raw answers and client passed`()=runBlocking {
        now=100L*86400000;enabled()
        assertFalse(event(at=now-90L*86400000-1));assertFalse(event(at=now+300001))
        assertFalse(event(props=JsonObject().apply{addProperty("answer","private")}))
        assertFalse(repo.capture(1,"lesson_passed",UUID.randomUUID().toString(),now))
        event();now+=90L*86400000;repo.prune(1);assertTrue(db.analyticsDao().events(1).isEmpty())
    }
    @Test fun `recovery only sees post ack completed lessons and nonempty finished reviews`()=runBlocking {
        enabled();val lesson=UUID.randomUUID().toString();val revision=UUID.randomUUID().toString();val session=UUID.randomUUID().toString()
        db.lessonDao().insertPack(ContentPack(1,revision,lesson,UUID.randomUUID().toString(),1,"{}","{}","hash",2,now,true))
        val payload=Gson().toJson(mapOf("id" to session,"clientCompletedAt" to now,"submittedOffline" to true,
            "answers" to (1..8).map{mapOf("questionId" to UUID.randomUUID().toString(),"optionKey" to "private")}))
        db.lessonDao().saveSession(LessonSession(1,session,lesson,revision,"LESSON","[]",0,"{}","QUEUED",now-1,now,payload))
        val review=UUID.randomUUID().toString();db.reviewDao().save(ReviewSession(review,1,"[]",0,false,now,now))
        repo.recover(1);assertEquals(listOf("lesson_completed"),db.analyticsDao().events(1).map{it.eventName})
        db.reviewDao().record(ReviewEvent(UUID.randomUUID().toString(),1,"book","GOOD",now,review));repo.recover(1)
        assertEquals(setOf("lesson_completed","review_session_completed"),db.analyticsDao().events(1).map{it.eventName}.toSet())
        repo.recover(1);assertEquals(2,db.analyticsDao().events(1).size);assertFalse(db.analyticsDao().events(1).any{it.propertiesJson.contains("private")||it.propertiesJson.contains("book")})
    }
    @Test fun `analytics blocked and business queues run independently in both directions`()=runBlocking {
        enabled();event(props=JsonObject().apply{addProperty("wordCount",99)})
        var wordCalls=0;var lessonCalls=0
        val business=SyncCoordinator({wordCalls++;SyncOutcome.COMPLETE},{lessonCalls++;SyncOutcome.COMPLETE})
        assertEquals(SyncOutcome.BLOCKED,repo.sync(1));assertEquals(SyncOutcome.COMPLETE,business.run(1));assertEquals(1,wordCalls);assertEquals(1,lessonCalls)
        event();val blockedBusiness=SyncCoordinator({SyncOutcome.BLOCKED},{SyncOutcome.BLOCKED})
        assertEquals(SyncOutcome.BLOCKED,blockedBusiness.run(1));repo.sync(1);assertEquals(1,db.analyticsDao().events(1).count{it.state=="ACKNOWLEDGED"})
    }
    @Test fun `opt out while batch is in flight cannot restore purged queue or enabled state`()=runBlocking {
        enabled();event();afterBatch={repo.optOut(1)}
        repo.sync(1);assertTrue(db.analyticsDao().events(1).isEmpty());assertFalse(db.analyticsDao().consent(1)!!.enabled)
        assertTrue(db.analyticsDao().consent(1)!!.pendingOptOut)
        afterBatch={};repo.sync(1);assertFalse(server[1]!!.optIn);assertTrue(db.analyticsDao().events(1).isEmpty())
    }
    @Test fun `tampered frozen payload is blocked locally without dispatch`()=runBlocking {
        enabled();event();val row=db.analyticsDao().events(1).single()
        db.analyticsDao().update(row.copy(payloadHash="tampered"))
        assertEquals(SyncOutcome.BLOCKED,repo.sync(1));assertTrue(batches.isEmpty())
        assertEquals("ANALYTICS_PAYLOAD_CORRUPT",db.analyticsDao().events(1).single().lastError)
    }
}
