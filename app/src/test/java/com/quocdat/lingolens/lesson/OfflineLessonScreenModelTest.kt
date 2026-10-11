package com.quocdat.lingolens.lesson

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class OfflineLessonScreenModelTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val name="lesson-ui-test.db"
    private val gson=Gson()
    private val packs=requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs.json")).bufferedReader().use{JsonParser.parseReader(it).asJsonArray}.map{gson.fromJson(it,LessonPack::class.java)}
    private lateinit var db:LearningDatabase
    private lateinit var repository:LessonStudyRepository
    private lateinit var packRepository:ContentPackRepository
    private lateinit var sessionRepository:LessonSessionRepository
    private var account=1L
    private var online=true
    private var failDownload=false
    private val scopes=mutableListOf<CoroutineScope>()
    private var scheduled=0
    private val course=CourseDto(packs[0].courseId,"Chặng DEMO","DEMO",packs[0].lessonId,packs[0].revisionId,false)
    private fun bytes(p:LessonPack)=gson.toJson(p).toByteArray(Charsets.UTF_8)
    private fun manifest()=PackManifest(1,course.id,packs.map{p->val b=bytes(p);PackReference(p.lessonId,p.revisionId,p.title,p.position,p.contentStatus,"/pack",b.size.toLong(),MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)})})
    private val gateway=object:StudyGateway {
        override suspend fun catalog(account:Long):CatalogDto { if(!online) throw IOException("offline");return CatalogDto(listOf(course),0) }
        override suspend fun enroll(account:Long,course:String){ if(!online) throw IOException("offline") }
        override suspend fun snapshot(account:Long,course:String):LessonCourseSnapshot { if(!online) throw IOException("offline");return LessonCourseSnapshot(course,"ACTIVE",emptyList(),0) }
    }
    private fun open(){
        db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
        packRepository=ContentPackRepository(db,object:PackGateway {
            override suspend fun manifest(courseId:String)=manifest()
            override suspend fun download(revisionId:String):ByteArray {
                val p=packs.first{it.revisionId==revisionId};if(failDownload && p.position==2) throw IOException("interrupted")
                return bytes(p)
            }
        },{account==it})
        sessionRepository=LessonSessionRepository(db,packRepository,{account==it},scheduleSync={scheduled++})
        repository=LessonStudyRepository(db,packRepository,sessionRepository,gateway,{account==it},{scheduled++})
    }
    private fun model(saved:SavedStateHandle=SavedStateHandle(),id:Long=1):OfflineLessonScreenModel {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);scopes+=scope
        return OfflineLessonScreenModel(saved,repository,id,{!online},scope)
    }
    @Before fun setup(){context.deleteDatabase(name);open()}
    private suspend fun stopProcess(){
        // Cancellation requests do not terminate in-flight SQLite work. Await every old observer
        // before closing/reopening the file, as real process death removes all old threads.
        val jobs=scopes.map{requireNotNull(it.coroutineContext[Job])}
        jobs.forEach{it.cancel()}
        jobs.forEach{it.join()}
        assertTrue("All old model coroutines must finish before reopening Room",jobs.all{it.isCompleted && it.children.none()})
        scopes.clear()
        db.close()
        assertFalse("The old Room instance must be closed",db.isOpen)
    }
    @After fun close()=runBlocking{stopProcess();context.deleteDatabase(name);Unit}
    private suspend fun prepare(model:OfflineLessonScreenModel){model.load();model.download(course.id)}
    private suspend fun answerAll(model:OfflineLessonScreenModel,index:Int){
        model.openLesson(course.id,packs[index].lessonId);model.showQuestions()
        packs[index].questions.forEach{q->model.choose(q.id,q.answerKey.optionKey);model.next()}
    }
    @Test fun `partial download cannot open course and retry preserves complete pack`()=runBlocking {
        val m=model();m.load();failDownload=true;m.download(course.id)
        assertNotNull(m.state.value.error);assertFalse(m.state.value.courses.single().ready)
        val first=db.lessonDao().pack(1,packs[0].revisionId)!!
        m.openLesson(course.id,packs[0].lessonId);assertNull(m.state.value.session)
        failDownload=false;m.download(course.id)
        assertEquals(first,db.lessonDao().pack(1,packs[0].revisionId));assertTrue(m.state.value.courses.single().ready)
        assertEquals(3,m.state.value.courses.single().lessons.size)
    }
    @Test fun `all three offline lessons show provisional counts unlock next and never official XP`()=runBlocking {
        val m=model();prepare(m);online=false
        for(index in 0..2){answerAll(m,index);m.submit();assertEquals("SUMMARY",m.state.value.stage)
            assertEquals("QUEUED",m.state.value.session!!.phase);assertEquals(10,m.state.value.correctCount);assertEquals(10,m.state.value.questionCount)
            assertEquals(0,m.state.value.officialXp);assertNull(m.state.value.confirmedResult)
            if(index<2) assertTrue(m.state.value.courses.single().lessons[index+1].canOpen)
        }
        assertEquals(3,db.lessonDao().pending(1).size);assertEquals(3,scheduled)
        assertTrue(db.lessonDao().pending(1).all{gson.fromJson(it.payloadJson,AttemptRequestDto::class.java).submittedOffline})
    }
    @Test fun `empty saved state and database reopen retain UUID answers position and frozen payload`()=runBlocking {
        val m=model();prepare(m);m.openLesson(course.id,packs[0].lessonId);m.showQuestions()
        val q=packs[0].questions.first();m.choose(q.id,"B");m.next()
        val before=m.state.value.session!!;val answers=m.state.value.answers
        stopProcess();open();online=false
        val fresh=model();fresh.load();fresh.openLesson(course.id,packs[0].lessonId)
        assertEquals(before.id,fresh.state.value.session!!.id);assertEquals(answers,fresh.state.value.answers);assertEquals(1,fresh.state.value.questionIndex)
        packs[0].questions.forEach{fresh.choose(it.id,it.answerKey.optionKey)};fresh.submit()
        val frozen=db.lessonDao().outbox(1,before.id)!!
        stopProcess();open()
        val restored=model();restored.load();restored.openLesson(course.id,packs[0].lessonId);restored.submit()
        assertEquals(before.id,restored.state.value.session!!.id);assertEquals(frozen,db.lessonDao().outbox(1,before.id))
    }
    @Test fun `worker confirmation is observed and server counts replace provisional counts`()=runBlocking {
        val m=model();prepare(m);answerAll(m,0);m.submit();val s=m.state.value.session!!
        val result=LessonSyncResult(s.id,s.lessonId,"LESSON",6,10,60,false,0,"CONFIRMED",false)
        db.withTransaction {
            db.lessonDao().saveSession(s.copy(phase="CONFIRMED",serverResultJson=gson.toJson(result)))
            db.lessonDao().acknowledge(1,s.id)
            db.lessonDao().saveCurriculum(CurriculumAccountState(1,course.id,"ACTIVE",gson.toJson(listOf(ConfirmedLessonProgress(s.lessonId,60,false))),0))
        }
        val confirmed=withTimeout(10_000){m.state.first{it.confirmedResult!=null}}
        assertEquals(6,confirmed.correctCount);assertEquals(0,confirmed.officialXp)
        assertFalse(confirmed.courses.single().lessons[1].canOpen)
    }
    @Test fun `server rejection keeps all answers frozen and cannot automatically retry or unlock`()=runBlocking {
        val m=model();prepare(m);answerAll(m,0);m.submit();val s=m.state.value.session!!;val answers=m.state.value.answers
        val queued=db.lessonDao().outbox(1,s.id)!!
        db.withTransaction {db.lessonDao().updateOutbox(queued.copy(state="BLOCKED",lastError="ATTEMPT_CONFLICT"));db.lessonDao().saveSession(s.copy(phase="BLOCKED"))}
        withTimeout(10_000){m.state.first{it.session?.phase=="BLOCKED"}}
        m.choose(packs[0].questions.first().id,"B");m.submit()
        assertEquals(answers,m.state.value.answers);assertEquals(queued.payloadJson,m.state.value.session!!.frozenPayloadJson)
        assertEquals("ATTEMPT_CONFLICT",m.state.value.syncError);assertEquals(0,m.state.value.officialXp)
        assertFalse(m.state.value.courses.single().lessons[1].canOpen)
        assertEquals(1,db.lessonDao().pending(1).size);assertEquals(1,scheduled)
    }
    @Test fun `waiting confirmation keeps zero official XP and a passed provisional next lesson`()=runBlocking {
        val m=model();prepare(m);answerAll(m,0);m.submit();val s=m.state.value.session!!
        val r=LessonSyncResult(s.id,s.lessonId,"LESSON",10,10,100,true,0,"WAITING_PREREQUISITE",true,"LESSON_PREREQUISITE_PENDING")
        db.lessonDao().saveSession(s.copy(phase="WAITING_PREREQUISITE",serverResultJson=gson.toJson(r)))
        withTimeout(10_000){m.state.first{it.session?.phase=="WAITING_PREREQUISITE"}}
        assertNull(m.state.value.confirmedResult);assertEquals(0,m.state.value.officialXp);assertTrue(m.state.value.courses.single().lessons[1].canOpen)
    }
    @Test fun `new revision download leaves active session pinned while explicit new attempt uses latest`()=runBlocking {
        val m=model();prepare(m);m.openLesson(course.id,packs[0].lessonId);val s=m.state.value.session!!
        m.choose(packs[0].questions.first().id,"B")
        val replacement=packs[0].copy(revisionId=java.util.UUID.randomUUID().toString(),title="Revision mới")
        val next=packs.toMutableList().also{it[0]=replacement}
        val updated=PackManifest(1,course.id,next.map{PackReference(it.lessonId,it.revisionId,it.title,it.position,it.contentStatus,"/pack",1,if(it==replacement) "new" else db.lessonDao().pack(1,it.revisionId)!!.checksum)})
        db.lessonDao().insertPack(ContentPack(1,replacement.revisionId,replacement.lessonId,course.id,1,gson.toJson(updated),gson.toJson(replacement),"new",1,System.currentTimeMillis()+1000,true))
        m.refreshLocal();m.backToCourses();m.openLesson(course.id,packs[0].lessonId)
        assertEquals(s.id,m.state.value.session!!.id);assertEquals(s.revisionId,m.state.value.pack!!.revisionId);assertEquals("B",m.state.value.answers[packs[0].questions.first().id])
        packs[0].questions.forEach{m.choose(it.id,it.answerKey.optionKey)};m.submit()
        val result=LessonSyncResult(s.id,s.lessonId,"LESSON",10,10,100,true,30,"CONFIRMED",false)
        db.lessonDao().saveSession(m.state.value.session!!.copy(phase="CONFIRMED",serverResultJson=gson.toJson(result)));m.refreshLocal();m.restart()
        assertNotEquals(s.id,m.state.value.session!!.id);assertEquals(replacement.revisionId,m.state.value.pack!!.revisionId)
        assertTrue(m.state.value.answers.isEmpty());assertEquals("LEARN",m.state.value.stage)
    }
    @Test fun `account switch shows no old course session answers or queue`()=runBlocking {
        val a=model();prepare(a);answerAll(a,0);a.submit();val s=a.state.value.session!!;val before=db.lessonDao().outbox(1,s.id)
        account=0;a.refreshLocal();assertTrue(a.state.value.courses.isEmpty());assertNull(a.state.value.session)
        account=2;online=false;val b=model(id=2);b.load()
        assertTrue(b.state.value.courses.isEmpty());assertNull(b.state.value.session);assertEquals(0,b.state.value.officialXp)
        assertEquals(before,db.lessonDao().outbox(1,s.id));assertTrue(db.lessonDao().pending(2).isEmpty())
        account=1;val returned=model();returned.load();returned.openLesson(course.id,s.lessonId);assertEquals(s.id,returned.state.value.session!!.id)
    }
    @Test fun `double submit queues once and navigation persists before returning`()=runBlocking {
        val m=model();prepare(m);answerAll(m,0);m.submit();val s=m.state.value.session!!;m.submit()
        assertEquals(1,db.lessonDao().pending(1).size);assertEquals(s.id,db.lessonDao().pending(1).single().attemptId)
        assertEquals(packs[0].questions.lastIndex,db.lessonDao().session(1,s.id)!!.position)
    }
}
