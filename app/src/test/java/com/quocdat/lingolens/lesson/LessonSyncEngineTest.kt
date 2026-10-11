package com.quocdat.lingolens.lesson

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.sync.*
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class LessonSyncEngineTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val name="lesson-sync-test.db"
    private val gson=Gson()
    private val packs=requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs.json")).bufferedReader().use{JsonParser.parseReader(it).asJsonArray}.map{gson.fromJson(it,LessonPack::class.java)}
    private lateinit var db:LearningDatabase
    private lateinit var sessions:LessonSessionRepository
    private lateinit var engine:LessonSyncEngine
    private var signedIn=1L
    private var time=1000L
    private var lost=false
    private var waiting=false
    private var statusCode=0
    private var failAck=false
    private var changeAccount=false
    private var wrongResponse=false
    private val server=linkedMapOf<String,LessonSyncResult>()
    private val calls=mutableListOf<String>()
    private val gateway=object:LessonSyncGateway {
        override suspend fun submit(account:Long,request:AttemptRequestDto):LessonSyncResult {
            calls+=request.lessonRevisionId
            if(statusCode!=0) throw HttpException(Response.error<Any>(statusCode,"{\"code\":\"TEST_ERROR\"}".toResponseBody()))
            val pack=packs.first{it.revisionId==request.lessonRevisionId}
            val result=LessonSyncResult(request.id,pack.lessonId,request.mode,10,10,100,true,if(waiting) 0 else 30,
                if(waiting) "WAITING_PREREQUISITE" else "CONFIRMED",waiting,if(waiting) "LESSON_PREREQUISITE_PENDING" else null)
            server[request.id]=result
            if(lost){lost=false;throw IOException("response lost after server commit")}
            if(changeAccount) signedIn=2
            return if(wrongResponse) result.copy(id="unrelated") else result
        }
        override suspend fun snapshot(account:Long,course:String)=LessonCourseSnapshot(course,"ACTIVE",
            server.values.filter{it.confirmationStatus=="CONFIRMED"}.distinctBy{it.lessonId}.map{ConfirmedLessonProgress(it.lessonId,100,true)},
            server.values.filter{it.confirmationStatus=="CONFIRMED"}.map{it.lessonId}.distinct().size*30)
    }
    private fun open(){
        db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
        val packRepo=ContentPackRepository(db,object:PackGateway{
            override suspend fun manifest(courseId:String):PackManifest=error("unused")
            override suspend fun download(revisionId:String):ByteArray=error("unused")
        },{signedIn==it})
        sessions=LessonSessionRepository(db,packRepo,{signedIn==it},clock={time++})
        engine=LessonSyncEngine(db,gateway,{signedIn==it},beforeAcknowledge={if(failAck) error("crash before acknowledgement")})
    }
    @Before fun setup()=runBlocking {
        context.deleteDatabase(name);open()
        val manifest=PackManifest(1,packs[0].courseId,packs.map{PackReference(it.lessonId,it.revisionId,it.title,it.position,it.contentStatus,"/pack",1,"test")})
        for(account in 1L..2L){packs.forEach{db.lessonDao().insertPack(ContentPack(account,it.revisionId,it.lessonId,it.courseId,1,gson.toJson(manifest),gson.toJson(it),"test",1,1,true))};db.lessonDao().saveCurriculum(CurriculumAccountState(account,packs[0].courseId,"ACTIVE","[]",0))}
    }
    @After fun close(){db.close();context.deleteDatabase(name)}
    private suspend fun enqueue(index:Int):LessonSession {
        val session=sessions.start(1,packs[index].revisionId)
        packs[index].questions.forEachIndexed{i,q->sessions.answer(1,session.id,q.id,q.answerKey.optionKey,i)}
        sessions.submit(1,session.id,true);return session
    }
    @Test fun `lost response and database reopen retry same UUID payload without adding XP locally`()=runBlocking {
        val session=enqueue(0);val original=db.lessonDao().pending(1).single();lost=true
        assertEquals(SyncOutcome.RETRY,engine.run(1));assertEquals(1,server.size);assertEquals(1,db.lessonDao().pending(1).single().retryCount)
        db.close();open();assertEquals(SyncOutcome.COMPLETE,engine.run(1));assertEquals(1,server.size)
        val ack=db.lessonDao().outbox(1,session.id)!!;assertEquals("CONFIRMED",ack.state);assertEquals(original.payloadJson,ack.payloadJson);assertEquals(original.payloadHash,ack.payloadHash)
        assertEquals(30,db.lessonDao().curriculum(1,packs[0].courseId)?.confirmedXpTotal)
        assertEquals(SyncOutcome.COMPLETE,engine.run(1));assertEquals(2,calls.size);assertEquals(30,db.lessonDao().curriculum(1,packs[0].courseId)?.confirmedXpTotal)
    }
    @Test fun `lesson dependency order wins over queued timestamps`()=runBlocking {
        val first=enqueue(0);val second=enqueue(1);val third=enqueue(2)
        db.lessonDao().updateOutbox(db.lessonDao().outbox(1,first.id)!!.copy(queuedAt=3000))
        db.lessonDao().updateOutbox(db.lessonDao().outbox(1,third.id)!!.copy(queuedAt=1))
        assertEquals(SyncOutcome.COMPLETE,engine.run(1));assertEquals(packs.map{it.revisionId},calls)
        assertEquals(90,db.lessonDao().curriculum(1,packs[0].courseId)?.confirmedXpTotal);assertEquals("CONFIRMED",db.lessonDao().outbox(1,second.id)?.state)
    }
    @Test fun `pending prerequisite stays retryable and freezes payload until confirmed`()=runBlocking {
        val session=enqueue(0);val payload=db.lessonDao().pending(1).single().payloadJson;waiting=true
        assertEquals(SyncOutcome.RETRY,engine.run(1));assertEquals("WAITING_PREREQUISITE",db.lessonDao().outbox(1,session.id)?.state)
        assertEquals(0,db.lessonDao().curriculum(1,packs[0].courseId)?.confirmedXpTotal)
        waiting=false;assertEquals(SyncOutcome.COMPLETE,engine.run(1));assertEquals(payload,db.lessonDao().outbox(1,session.id)?.payloadJson);assertEquals(1,server.size)
    }
    @Test fun `permanent conflict blocks and retains payload while auth only pauses`()=runBlocking {
        val session=enqueue(0);val before=db.lessonDao().pending(1).single()
        statusCode=401;assertEquals(SyncOutcome.PAUSED,engine.run(1));assertEquals(before,db.lessonDao().outbox(1,session.id))
        statusCode=409;assertEquals(SyncOutcome.BLOCKED,engine.run(1));val blocked=db.lessonDao().outbox(1,session.id)!!
        assertEquals("BLOCKED",blocked.state);assertEquals(before.payloadJson,blocked.payloadJson);assertEquals(before.attemptId,blocked.attemptId);assertTrue(blocked.lastError!!.contains("TEST_ERROR"))
        statusCode=0;val count=calls.size;assertEquals(SyncOutcome.BLOCKED,engine.run(1));assertEquals(count,calls.size)
    }
    @Test fun `network HTTP retry statuses preserve pending UUID and payload`()=runBlocking {
        val session=enqueue(0);val payload=db.lessonDao().pending(1).single().payloadJson
        for(code in listOf(408,429,500)){statusCode=code;assertEquals(SyncOutcome.RETRY,engine.run(1));val op=db.lessonDao().outbox(1,session.id)!!;assertEquals("PENDING",op.state);assertEquals(payload,op.payloadJson)}
        statusCode=0;assertEquals(SyncOutcome.COMPLETE,engine.run(1))
    }
    @Test fun `account change after response cannot write acknowledgement or another account data`()=runBlocking {
        val session=enqueue(0);val before=db.lessonDao().pending(1).single();changeAccount=true
        assertEquals(SyncOutcome.PAUSED,engine.run(1));assertEquals(before,db.lessonDao().outbox(1,session.id));assertNull(db.lessonDao().outbox(2,session.id))
        assertEquals(0,db.lessonDao().curriculum(2,packs[0].courseId)?.confirmedXpTotal)
        signedIn=1;changeAccount=false;assertEquals(SyncOutcome.COMPLETE,engine.run(1));assertEquals(1,server.size)
    }
    @Test fun `result snapshot and acknowledgement rollback together before local commit`()=runBlocking {
        val session=enqueue(0);failAck=true
        assertEquals(SyncOutcome.RETRY,engine.run(1));assertNull(db.lessonDao().session(1,session.id)?.serverResultJson)
        assertEquals("QUEUED",db.lessonDao().session(1,session.id)?.phase);assertEquals("PENDING",db.lessonDao().outbox(1,session.id)?.state);assertEquals(0,db.lessonDao().curriculum(1,packs[0].courseId)?.confirmedXpTotal)
        failAck=false;assertEquals(SyncOutcome.COMPLETE,engine.run(1));assertEquals(30,db.lessonDao().curriculum(1,packs[0].courseId)?.confirmedXpTotal)
    }
    @Test fun `unexpected acknowledgement never confirms another UUID`()=runBlocking {
        val session=enqueue(0);wrongResponse=true
        assertEquals(SyncOutcome.RETRY,engine.run(1));assertEquals("PENDING",db.lessonDao().outbox(1,session.id)?.state);assertNull(db.lessonDao().session(1,session.id)?.serverResultJson)
    }
    @Test fun `blocked legacy queue does not stop lesson queue and reverse also holds`()=runBlocking {
        var lessonCalls=0;var legacyCalls=0
        val a=SyncCoordinator({legacyCalls++;SyncOutcome.BLOCKED},{lessonCalls++;SyncOutcome.COMPLETE})
        assertEquals(SyncOutcome.BLOCKED,a.run(1));assertEquals(1,lessonCalls);assertEquals(1,legacyCalls)
        val b=SyncCoordinator({legacyCalls++;SyncOutcome.COMPLETE},{lessonCalls++;SyncOutcome.BLOCKED})
        assertEquals(SyncOutcome.BLOCKED,b.run(1));assertEquals(2,lessonCalls);assertEquals(2,legacyCalls)
        val c=SyncCoordinator({throw IOException("legacy unavailable")},{lessonCalls++;SyncOutcome.COMPLETE})
        assertEquals(SyncOutcome.RETRY,c.run(1));assertEquals(3,lessonCalls)
    }
}
