package com.quocdat.lingolens.deletion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.quocdat.lingolens.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class AccountDataCleanerTest {
    private lateinit var db:LearningDatabase
    private val tables=listOf("SavedWordEntity","LearningEvent","OutboxOperation","SyncAccount","ReviewCard","ReviewEvent","ReviewSession","ContentPack","LessonSession","LessonAttemptOutbox","CurriculumAccountState","AnalyticsConsentState","AnalyticsOutbox")
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),LearningDatabase::class.java).build()
        for(account in listOf(1L,2L)){
            val id=UUID.randomUUID().toString()
            db.learningDao().save(SavedWordEntity(account,"book","{}",10));db.learningDao().record(LearningEvent(account,"book",10))
            db.reviewDao().save(ReviewCard(account,"book",20));db.reviewDao().save(ReviewSession(id,account,"[]",0,false,10))
            db.reviewDao().record(ReviewEvent(UUID.randomUUID().toString(),account,"book","GOOD",10,id))
            db.syncDao().enqueue(OutboxOperation(operationId=id,accountId=account,kind="SAVE_WORD",payload="{}",state=if(account==1L)"BLOCKED" else "PENDING",attempts=3))
            db.syncDao().saveAccount(SyncAccount(account,true,10))
            db.lessonDao().insertPack(ContentPack(account,"revision","lesson","course",1,"{}","{}","hash",2,10,true))
            db.lessonDao().saveSession(LessonSession(account,id,"lesson","revision","LESSON","[]",0,"{}","QUEUED",10,10,"{}"))
            db.lessonDao().enqueue(LessonAttemptOutbox(account,id,id,"{}","hash","PENDING",10,2))
            db.lessonDao().saveCurriculum(CurriculumAccountState(account,"course","ACTIVE","[]",30))
            db.analyticsDao().saveConsent(AnalyticsConsentState(account,true,"analytics-v1",1,10,false,10))
            db.analyticsDao().enqueue(AnalyticsOutbox(account,id,"app_open",id,null,10,"UTC","test","{}","hash",1,queuedAt=10))
        }
        db.learningDao().cache(listOf(VocabularyCache("book","{}",10)))
        db.topicDao().saveTopic(TopicCache("objects","objects","Đồ vật",1,10))
        db.topicDao().saveVocabulary(TopicVocabularyCache("objects","book",1))
    }
    @After fun close(){db.close()}
    private fun snapshot(account:Long)=tables.associateWith{table->db.openHelper.readableDatabase.query("SELECT * FROM $table WHERE accountId=?",arrayOf(account)).use{cursor->
        buildList{while(cursor.moveToNext())add((0 until cursor.columnCount).map{if(cursor.isNull(it))null else cursor.getString(it)})}
    }}
    @Test fun `cleanup rolls back on failure then removes every owned table respecting FK and preserving B and public caches`()=runBlocking {
        val a=snapshot(1);val b=snapshot(2)
        try{AccountDataCleaner(db){error("Injected cleanup failure")}.purge(1);fail("Expected rollback")}catch(_:IllegalStateException){}
        assertEquals(a,snapshot(1));assertEquals(b,snapshot(2))
        AccountDataCleaner(db).purge(1);snapshot(1).forEach{(table,rows)->assertTrue(table,rows.isEmpty())}
        assertEquals(b,snapshot(2));assertNotNull(db.learningDao().cached("book"));assertNotNull(db.topicDao().topic("objects"))
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        AccountDataCleaner(db).purge(1);assertEquals(b,snapshot(2))
    }
}
