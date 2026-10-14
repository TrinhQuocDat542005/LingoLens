package com.quocdat.lingolens.analytics

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import com.quocdat.lingolens.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class AnalyticsDatabaseMigrationTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val name="analytics-migration.db"
    private var database:LearningDatabase?=null
    private val newTables=listOf("AnalyticsConsentState","AnalyticsOutbox","TopicCache","TopicVocabularyCache")
    @After fun close(){database?.close();database=null;context.deleteDatabase(name)}
    private fun schema(version:Int)=requireNotNull(javaClass.getResourceAsStream("/com.quocdat.lingolens.data.local.LearningDatabase/$version.json"))
        .bufferedReader().use{JsonParser.parseReader(it).asJsonObject["database"].asJsonObject}
    private fun fixture(version:Int){
        database?.close();database=null;context.deleteDatabase(name)
        val schema=schema(version);val old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        try{
            old.execSQL("PRAGMA foreign_keys=ON")
            schema["entities"].asJsonArray.forEach{node->val e=node.asJsonObject;val table=e["tableName"].asString
                old.execSQL(e["createSql"].asString.replace("\${TABLE_NAME}",table))
                e["indices"]?.asJsonArray?.forEach{old.execSQL(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}",table))}
            }
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema["identityHash"].asString))
            for(account in 1..2){
                old.execSQL("INSERT INTO SavedWordEntity VALUES(?,'book',?,?)",arrayOf<Any>(account,"{\"owner\":$account}",100L+account))
                old.execSQL("INSERT INTO LearningEvent VALUES(?,'book',?)",arrayOf<Any>(account,50L+account))
                if(version>=2){
                    for((index,state) in listOf("PENDING","BLOCKED").withIndex()){
                        old.execSQL("INSERT INTO OutboxOperation VALUES(?,?,?,'SAVE_WORD',?,?,?,?)",arrayOf<Any>(account*10+index,"op-$account-$index",account,"{\"owner\":$account,\"id\":$index}",state,3+index,"error-$index"))
                    }
                    old.execSQL("INSERT INTO SyncAccount VALUES(?,1,123456,'retained')",arrayOf(account))
                }
                if(version>=3){
                    old.execSQL("INSERT INTO ReviewCard VALUES(?,'book',777,4,2.1,3,1,'REVIEW',555)",arrayOf(account))
                    old.execSQL("INSERT INTO ReviewEvent VALUES(?,?,'book','GOOD',555,?)",arrayOf<Any>("event-$account",account,"review-$account"))
                    old.execSQL("INSERT INTO ReviewSession VALUES(?,?,'[\"book\"]',1,1,444,NULL)",arrayOf<Any>("review-$account",account))
                }
                if(version>=4){
                    old.execSQL("INSERT INTO ContentPack VALUES(?,'revision','lesson','course',1,'{\"manifest\":1}','{\"content\":1}','checksum',99,123,1)",arrayOf(account))
                    old.execSQL("INSERT INTO LessonSession VALUES(?,'unfinished','lesson','revision','LESSON','[\"q1\",\"q2\"]',1,?,'LEARN',100,200,NULL,NULL)",arrayOf<Any>(account,"{\"q1\":\"A\",\"owner\":$account}"))
                    for((index,state) in listOf("PENDING","BLOCKED").withIndex()){
                        val id="attempt-$index";val payload="{\"id\":\"$id\",\"owner\":$account}"
                        old.execSQL("INSERT INTO LessonSession VALUES(?,?,'lesson','revision','LESSON','[\"q1\"]',1,'{\"q1\":\"A\"}','QUEUED',100,200,?,NULL)",arrayOf<Any>(account,id,payload))
                        old.execSQL("INSERT INTO LessonAttemptOutbox VALUES(?,?,?,?,?,?,?,?,?)",arrayOf<Any>(account,id,id,payload,"hash-$account-$index",state,222+index,4+index,"lesson-error-$index"))
                    }
                    old.execSQL("INSERT INTO CurriculumAccountState VALUES(?,'course','ACTIVE','[{\"confirmed\":true}]',?,321)",arrayOf<Any>(account,30*account))
                }
            }
            old.execSQL("INSERT INTO VocabularyCache VALUES('book','{\"translation\":\"sách\"}',222)")
            old.version=version
        }finally{old.close()}
    }
    private fun open(last:Migration=LearningDatabase.MIGRATION_4_5):LearningDatabase {
        val db=Room.databaseBuilder(context,LearningDatabase::class.java,name)
            .addMigrations(LearningDatabase.MIGRATION_1_2,LearningDatabase.MIGRATION_2_3,LearningDatabase.MIGRATION_3_4,last).build()
        database=db;db.openHelper.writableDatabase;return db
    }
    private data class Rows(val columns:List<String>,val rows:List<List<String?>>)
    private fun snapshot(version:Int):Map<String,Rows>{
        val old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        return try{schema(version)["entities"].asJsonArray.associate{node->val table=node.asJsonObject["tableName"].asString
            table to old.rawQuery("SELECT * FROM $table ORDER BY 1,2",null).use{cursor->Rows(cursor.columnNames.toList(),buildList{
                while(cursor.moveToNext())add((0 until cursor.columnCount).map{if(cursor.isNull(it))null else cursor.getString(it)})})}
        }}finally{old.close()}
    }
    private fun definitions(version:Int):List<String>{
        val old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        return try{schema(version)["entities"].asJsonArray.flatMap{node->val table=node.asJsonObject["tableName"].asString
            old.rawQuery("SELECT name,sql FROM sqlite_master WHERE tbl_name=? ORDER BY type,name",arrayOf(table)).use{c->buildList{while(c.moveToNext())add(c.getString(0)+":"+if(c.isNull(1))"NULL" else c.getString(1))}}
        }}finally{old.close()}
    }
    @Test fun `exported v4 two account upgrade preserves every column row and old schema`()=runBlocking {
        fixture(4);val before=snapshot(4);val ddl=definitions(4);val db=open()
        assertEquals(5,db.openHelper.writableDatabase.version);assertEquals(before,snapshot(4));assertEquals(ddl,definitions(4))
        assertEquals("review-1",db.reviewDao().activeSession(1)!!.id)
        assertEquals(listOf("PENDING","BLOCKED"),db.syncDao().pending(2).map{it.state})
        assertEquals("LEARN",db.lessonDao().session(1,"unfinished")!!.phase)
        assertEquals(5,db.lessonDao().outbox(2,"attempt-1")!!.retryCount)
        for(table in newTables)db.openHelper.writableDatabase.query("SELECT count(*) FROM $table").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
        db.close();database=null;val reopened=open();assertEquals(before,snapshot(4))
        reopened.learningDao().save(SavedWordEntity(2,"phone","{\"new\":true}",999))
        assertEquals(999L,reopened.learningDao().find(2,"phone")!!.savedAt)
    }
    @Test fun `all exported versions one through four upgrade to five without losing original rows`()=runBlocking {
        for(version in 1..4){fixture(version);val before=snapshot(version);val ddl=definitions(version);val db=open()
            assertEquals(5,db.openHelper.writableDatabase.version);assertEquals(before,snapshot(version));assertEquals(ddl,definitions(version))
            if(version<3)assertEquals(102L,db.reviewDao().card(2,"book")!!.dueAt)
            db.close();database=null;open();assertEquals(before,snapshot(version))
        }
    }
    @Test fun `failure halfway through four to five rolls back DDL version identity and data`() {
        fixture(4);val before=snapshot(4);val ddl=definitions(4)
        val failure=object:Migration(4,5){override fun migrate(db:SupportSQLiteDatabase){var calls=0
            LearningDatabase.MIGRATION_4_5.migrate(object:SupportSQLiteDatabase by db{
                override fun execSQL(sql:String){db.execSQL(sql);if(++calls==3)error("Injected migration failure")}
            })
        }}
        var caught=false
        try{open(failure)}catch(error:Throwable){caught=generateSequence(error){it.cause}.any{it.message=="Injected migration failure"}}
        finally{database?.close();database=null}
        assertTrue(caught);assertEquals(before,snapshot(4));assertEquals(ddl,definitions(4))
        val raw=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        try{assertEquals(4,raw.version);raw.rawQuery("SELECT identity_hash FROM room_master_table WHERE id=42",null).use{assertTrue(it.moveToFirst());assertEquals(schema(4)["identityHash"].asString,it.getString(0))}
            for(table in newTables)raw.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?",arrayOf(table)).use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
        }finally{raw.close()}
        val db=open();assertEquals(5,db.openHelper.writableDatabase.version);assertEquals(before,snapshot(4))
    }
    private fun event(account:Long)=AnalyticsOutbox(account,"same-event","app_open","same-session",null,100,"UTC","1.0","{}","hash",1,"PENDING",0,101,null)
    @Test fun `new consent queue and topic tables read write reopen isolate accounts and cascade only their rows`()=runBlocking {
        fixture(4);var db=open();val old=snapshot(4)
        for(account in 1L..2L){db.analyticsDao().saveConsent(AnalyticsConsentState(account,true,"analytics-v1",1,100,false,101));db.analyticsDao().enqueue(event(account))}
        db.topicDao().saveTopic(TopicCache("objects","objects","Đồ vật",1,100));db.topicDao().saveVocabulary(TopicVocabularyCache("objects","book",0))
        db.close();database=null;db=open()
        assertEquals(event(1),db.analyticsDao().events(1).single());assertEquals(event(2),db.analyticsDao().events(2).single())
        assertTrue(db.analyticsDao().events(3).isEmpty());assertNull(db.analyticsDao().consent(3))
        assertEquals("Đồ vật",db.topicDao().topic("objects")!!.title);assertEquals("book",db.topicDao().vocabularies("objects").single().word)
        db.analyticsDao().deleteConsent(1);assertTrue(db.analyticsDao().events(1).isEmpty());assertEquals(1,db.analyticsDao().events(2).size)
        assertEquals(old,snapshot(4));db.topicDao().deleteTopic("objects");assertTrue(db.topicDao().vocabularies("objects").isEmpty())
    }
    @Test fun `analytics and topic foreign keys reject missing parents`()=runBlocking {
        fixture(4);val db=open()
        var failed=false;try{db.analyticsDao().enqueue(event(7))}catch(_:android.database.sqlite.SQLiteConstraintException){failed=true}
        assertTrue(failed);assertTrue(db.analyticsDao().events(7).isEmpty())
        failed=false;try{db.topicDao().saveVocabulary(TopicVocabularyCache("missing","book",0))}catch(_:android.database.sqlite.SQLiteConstraintException){failed=true}
        assertTrue(failed);assertTrue(db.topicDao().vocabularies("missing").isEmpty())
    }
    @Test fun `compiler exported v5 contains exactly four new tables and unchanged v4 entity definitions`() {
        val old=schema(4)["entities"].asJsonArray.associateBy{it.asJsonObject["tableName"].asString}
        val next=schema(5)["entities"].asJsonArray.associateBy{it.asJsonObject["tableName"].asString}
        assertEquals(old.keys+newTables,next.keys);old.forEach{(table,entity)->assertEquals(table,entity,next[table])}
    }
    @Test fun `fixture schemas one through four remain byte identical to committed exports after LF normalization`() {
        val hashes=listOf("f6e3420d8ba5d426c1452edbca3e4c14b90f443491548d6a0f725bdcb5cb7bee",
            "6c9869afd4800e898e44f02c5a99c29f6b313fdd6f75ab322eac579fd31a1508",
            "c952d5e1d72460b2c8ea51229b67eaaf51ef73dc2660e6916a5bdb5b063f15e4",
            "caaa6d5e59c5e75837817e2b59cc721be29a6911683c64d96f3bea39f5a74f10")
        for(version in 1..4){
            val text=requireNotNull(javaClass.getResourceAsStream("/com.quocdat.lingolens.data.local.LearningDatabase/$version.json"))
                .bufferedReader(Charsets.UTF_8).use{it.readText()}.replace("\r\n","\n")
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
            assertEquals("schema $version",hashes[version-1],hash)
        }
    }
}
