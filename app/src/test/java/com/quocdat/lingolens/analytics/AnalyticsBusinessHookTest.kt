package com.quocdat.lingolens.analytics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.repository.*
import com.quocdat.lingolens.lesson.*
import com.quocdat.lingolens.model.LearnedWord
import com.quocdat.lingolens.review.core.ReviewGrade
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class AnalyticsBusinessHookTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val name="analytics-hooks.db"
    private lateinit var db:LearningDatabase
    private val gson=Gson()
    private val packs=requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs.json")).bufferedReader().use{JsonParser.parseReader(it).asJsonArray}.map{gson.fromJson(it,LessonPack::class.java)}
    @Before fun setup()=runBlocking {
        context.deleteDatabase(name);db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
        val contents=packs.associate { it.revisionId to gson.toJson(it) }
        val manifest=PackManifest(1,packs.first().courseId,packs.map { pack ->
            val bytes=contents.getValue(pack.revisionId).toByteArray(Charsets.UTF_8)
            val checksum=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
            PackReference(lessonId=pack.lessonId,revisionId=pack.revisionId,title=pack.title,position=pack.position,
                contentStatus=pack.contentStatus,downloadUrl="/api/v1/curriculum/revisions/${pack.revisionId}/pack",
                byteLength=bytes.size.toLong(),checksum=checksum)
        })
        val manifestJson=gson.toJson(manifest)
        for(reference in manifest.lessons)db.lessonDao().insertPack(ContentPack(1,reference.revisionId,reference.lessonId,manifest.courseId,1,
            manifestJson,contents.getValue(reference.revisionId),reference.checksum,reference.byteLength,1000,true))
        db.lessonDao().saveCurriculum(CurriculumAccountState(1,packs[0].courseId,"ACTIVE","[]",0))
    }
    @After fun close(){db.close();context.deleteDatabase(name)}
    @Test fun `lesson telemetry failure after commit cannot rollback durable start freeze or business queue`()=runBlocking {
        val packRepo=ContentPackRepository(db,object:PackGateway {
            override suspend fun manifest(courseId:String):PackManifest=error("unused")
            override suspend fun download(revisionId:String):ByteArray=error("unused")
        },isSignedIn={it==1L})
        var starts=0;var completions=0
        val repository=LessonSessionRepository(db,packRepo,{it==1L},onStarted={assertFalse(db.inTransaction());starts++;error("Injected analytics failure")},
            onCompleted={_,_->assertFalse(db.inTransaction());completions++;error("Injected analytics failure")})
        val session=repository.start(1,packs[0].revisionId);assertEquals(1,starts);assertNotNull(db.lessonDao().session(1,session.id))
        packs[0].questions.forEachIndexed{index,q->repository.answer(1,session.id,q.id,q.answerKey.optionKey,index)}
        val payload=repository.submit(1,session.id,true);assertEquals(1,completions);assertEquals(payload,db.lessonDao().outbox(1,session.id)!!.payloadJson)
        assertEquals(payload,db.lessonDao().session(1,session.id)!!.frozenPayloadJson)
        assertEquals("QUEUED",db.lessonDao().session(1,session.id)!!.phase);assertEquals(1,completions)
    }
    @Test fun `review telemetry failure cannot rollback schedule event or review outbox`()=runBlocking {
        val now=System.currentTimeMillis()
        val word=LearnedWord("book","book","sách","Noun","A book","A1",emptyList(),"Read.","")
        db.learningDao().save(SavedWordEntity(1,"book",gson.toJson(word),now))
        db.reviewDao().initialize(ReviewCard(1,"book",0))
        var calls=0
        val review=ReviewRepository(db,onFinished={assertFalse(db.inTransaction());if(it.session.finishedAt!=null && it.answers.isNotEmpty()){calls++;error("Injected analytics failure")}})
        val session=review.start(1);review.reveal(1,session.session.id)
        val result=review.grade(1,session.session.id,0,ReviewGrade.GOOD)
        assertNotNull(result.session.finishedAt);assertEquals(1,calls)
        assertEquals(1,db.reviewDao().sessionEvents(1,session.session.id).size);assertEquals("REVIEW_WORD",db.syncDao().pending(1).single().kind)
        assertEquals(1,db.reviewDao().card(1,"book")!!.repetitions)
    }
    @Test fun `UI prompt choice is per stable account and retained across preference reopen`() {
        context.getSharedPreferences("analytics_ui",Context.MODE_PRIVATE).edit().clear().commit()
        var preferences=AnalyticsPromptPreferences(context)
        assertFalse(preferences.seen(1));assertFalse(preferences.seen(2))
        preferences.rememberChoice(1);preferences=AnalyticsPromptPreferences(context)
        assertTrue(preferences.seen(1));assertFalse(preferences.seen(2));assertFalse(preferences.seen(3))
        assertTrue(AnalyticsConsentCopy.WORDING.contains("không gồm ảnh, đáp án bạn nhập, mật khẩu hoặc token"))
        assertEquals(listOf("Cho phép","Không, cảm ơn"),AnalyticsConsentCopy.BUTTONS)
    }
}
