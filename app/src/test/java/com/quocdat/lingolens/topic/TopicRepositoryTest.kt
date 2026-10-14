package com.quocdat.lingolens.topic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.repository.LearningRepository
import com.quocdat.lingolens.model.LearnedWord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class TopicRepositoryTest {
    private lateinit var db:LearningDatabase
    private lateinit var repo:TopicRepository
    private val mapping=PublishedTopic("00000000-0000-4000-8000-000000000100","objects","Đồ vật",1,listOf("book","bottle","phone"))
    private var offline=false
    private var payload=listOf(mapping)
    @Before fun setup(){
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),LearningDatabase::class.java).build()
        repo=TopicRepository(db,object:TopicGateway{override suspend fun published():List<PublishedTopic>{if(offline)throw IOException("offline");return payload}})
    }
    @After fun close(){db.close()}
    private fun word(key:String)=LearnedWord(key,key,"nghĩa","Noun","Definition","B1",emptyList(),"Example","")
    @Test fun `published mapping is cached and notebook joins only the selected account`()=runBlocking {
        val learning=LearningRepository(db)
        learning.save(1,word("book"),100);learning.save(2,word("phone"),200);learning.save(1,word("cat"),300)
        val before=db.syncDao().pending(1);val card=db.reviewDao().card(1,"book")
        repo.refresh()
        assertEquals(listOf("book"),repo.words(1,mapping.id).first().map{it.word})
        assertEquals(listOf("phone"),repo.words(2,mapping.id).first().map{it.word})
        assertEquals("Đồ vật",repo.forWord(" BOOK ").first().single().title)
        assertTrue(repo.forWord("cat").first().isEmpty())
        assertEquals(before,db.syncDao().pending(1));assertEquals(card,db.reviewDao().card(1,"book"))
    }
    @Test fun `offline or missing mapping cannot prevent existing save and review flow`()=runBlocking {
        offline=true;assertFalse(repo.refresh())
        val learning=LearningRepository(db);learning.save(1,word("book"),100)
        assertNotNull(learning.find(1,"book"));assertNotNull(db.reviewDao().card(1,"book"))
        assertEquals(1,db.syncDao().pending(1).size);assertTrue(repo.words(1,mapping.id).first().isEmpty())
        offline=false;repo.refresh();val queue=db.syncDao().pending(1)
        offline=true;assertFalse(repo.refresh());assertEquals(listOf("book"),repo.words(1,mapping.id).first().map{it.word})
        assertEquals(queue,db.syncDao().pending(1))
    }
    @Test fun `invalid replacement is rejected atomically and valid refresh does not duplicate cache`()=runBlocking {
        assertTrue(repo.refresh());assertTrue(repo.refresh());assertEquals(3,db.topicDao().vocabularies(mapping.id).size)
        payload=listOf(mapping.copy(words=listOf("book","book")))
        assertFalse(repo.refresh());assertEquals(3,db.topicDao().vocabularies(mapping.id).size)
        payload=listOf(mapping.copy(words=listOf("book")))
        assertTrue(repo.refresh());assertEquals(listOf("book"),db.topicDao().vocabularies(mapping.id).map{it.word})
        payload=emptyList();assertTrue(repo.refresh());assertTrue(repo.forWord("book").first().isEmpty())
    }
}
