package com.quocdat.lingolens.lesson

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class LessonSessionRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val name="durable-lesson-test.db"
    private val gson=Gson()
    private val packs=requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs.json")).bufferedReader().use{JsonParser.parseReader(it).asJsonArray}.map{gson.fromJson(it,LessonPack::class.java)}
    private lateinit var db:LearningDatabase
    private lateinit var repository:LessonSessionRepository
    private var signedIn=1L
    private var time=1000L
    private var scheduled=0
    private var fail=false
    private fun open(){
        db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
        repository=LessonSessionRepository(db,ContentPackRepository(db,object:PackGateway{
            override suspend fun manifest(courseId:String):PackManifest=error("unused")
            override suspend fun download(revisionId:String):ByteArray=error("unused")
        },{signedIn==it}),{signedIn==it},clock={time},scheduleSync={scheduled++},afterEnqueue={if(fail) error("crash after enqueue")})
    }
    @Before fun setup()=runBlocking {
        context.deleteDatabase(name);open()
        for(account in 1L..2L){
            val manifest=PackManifest(1,packs[0].courseId,packs.map{p->PackReference(p.lessonId,p.revisionId,p.title,p.position,p.contentStatus,"/pack",1,"test")})
            packs.forEach{p->db.lessonDao().insertPack(ContentPack(account,p.revisionId,p.lessonId,p.courseId,1,gson.toJson(manifest),gson.toJson(p),"test",1,1,true))}
            db.lessonDao().saveCurriculum(CurriculumAccountState(account,packs[0].courseId,"ACTIVE","[]",0))
        }
    }
    @After fun close(){db.close();context.deleteDatabase(name)}
    private suspend fun answerAll(session:LessonSession){packs.first{it.revisionId==session.revisionId}.questions.forEachIndexed{i,q->repository.answer(1,session.id,q.id,q.answerKey.optionKey,i)}}
    @Test fun `answers position UUID and revision survive actual database close reopen`()=runBlocking {
        val session=repository.start(1,packs[0].revisionId)
        repository.answer(1,session.id,packs[0].questions[0].id,"B",3)
        val before=repository.resume(1,session.id)
        db.close();open();assertEquals(before,repository.resume(1,session.id))
        assertEquals(session.id,repository.resume(1,session.id).id);assertEquals(3,repository.resume(1,session.id).position)
        assertEquals("B",JsonParser.parseString(repository.resume(1,session.id).answersJson).asJsonObject[packs[0].questions[0].id].asString)
        assertTrue(db.lessonDao().pending(1).isEmpty())
    }
    @Test fun `submit freezes same payload and UUID across retry and crash after commit`()=runBlocking {
        val session=repository.start(1,packs[0].revisionId);answerAll(session)
        val payload=repository.submit(1,session.id,true);assertEquals(1,scheduled)
        val before=db.lessonDao().pending(1).single();assertEquals(session.id,before.attemptId);assertEquals(payload,before.payloadJson)
        time=9000;assertEquals(payload,repository.submit(1,session.id,false));assertEquals(before,db.lessonDao().pending(1).single())
        db.close();open();assertEquals(payload,repository.submit(1,session.id,false));assertEquals(before,db.lessonDao().pending(1).single())
        val request=gson.fromJson(payload,AttemptRequestDto::class.java);assertEquals(1000L,request.clientCompletedAt);assertTrue(request.submittedOffline)
        var rejected=false;try{repository.answer(1,session.id,packs[0].questions[0].id,"B",0)}catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
        assertEquals(before.payloadJson,db.lessonDao().pending(1).single().payloadJson)
    }
    @Test fun `crash before commit writes neither frozen session nor outbox`()=runBlocking {
        val session=repository.start(1,packs[0].revisionId);answerAll(session);val before=repository.resume(1,session.id)
        fail=true;var rejected=false;try{repository.submit(1,session.id,true)}catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
        assertEquals(before,repository.resume(1,session.id));assertTrue(db.lessonDao().pending(1).isEmpty());assertEquals(0,scheduled)
        fail=false;repository.submit(1,session.id,true);assertEquals(1,db.lessonDao().pending(1).size)
    }
    @Test fun `account switch and logout keep pending data isolated`()=runBlocking {
        val session=repository.start(1,packs[0].revisionId);answerAll(session);repository.submit(1,session.id,true)
        signedIn=2;var rejected=false;try{repository.resume(2,session.id)}catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
        assertTrue(db.lessonDao().pending(2).isEmpty());assertEquals(1,db.lessonDao().pending(1).size)
        signedIn=0;rejected=false;try{repository.resume(1,session.id)}catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
        signedIn=1;assertEquals(session.id,repository.resume(1,session.id).id)
    }
    @Test fun `offline provisional pass opens next lesson without official XP`()=runBlocking {
        var rejected=false;try{repository.start(1,packs[1].revisionId)}catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
        val first=repository.start(1,packs[0].revisionId);answerAll(first);repository.submit(1,first.id,true)
        assertTrue(repository.provisionalGrade(1,first.id).passed)
        assertEquals(packs[1].revisionId,repository.start(1,packs[1].revisionId).revisionId)
        assertEquals(0,db.lessonDao().curriculum(1,packs[0].courseId)?.confirmedXpTotal)
        rejected=false;try{repository.start(1,packs[2].revisionId)}catch(_:IllegalStateException){rejected=true};assertTrue(rejected)
    }
    @Test fun `invalid answers and incomplete submission do not mutate durable state`()=runBlocking {
        val session=repository.start(1,packs[0].revisionId);val before=repository.resume(1,session.id)
        for(q in listOf(packs[0].questions[0].id,"not-a-question")){
            var rejected=false;try{repository.answer(1,session.id,q,"INVALID",0)}catch(_:IllegalArgumentException){rejected=true};assertTrue(rejected)
        }
        var rejected=false;try{repository.submit(1,session.id,true)}catch(_:IllegalArgumentException){rejected=true};assertTrue(rejected)
        assertEquals(before,repository.resume(1,session.id));assertTrue(db.lessonDao().pending(1).isEmpty())
    }
}
