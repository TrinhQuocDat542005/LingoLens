package com.quocdat.lingolens.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.repository.*
import com.quocdat.lingolens.model.LearnedWord
import com.quocdat.lingolens.review.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class ReviewRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: LearningDatabase
    private lateinit var review: ReviewRepository
    private lateinit var learning: LearningRepository
    private fun open() {
        db = Room.databaseBuilder(context, LearningDatabase::class.java, "review.db")
            .addMigrations(LearningDatabase.MIGRATION_1_2, LearningDatabase.MIGRATION_2_3, LearningDatabase.MIGRATION_3_4, LearningDatabase.MIGRATION_4_5).allowMainThreadQueries().build()
        review = ReviewRepository(db); learning = LearningRepository(db)
    }
    @Before fun before() { context.deleteDatabase("review.db"); open() }
    @After fun after() { db.close(); context.deleteDatabase("review.db") }
    private fun word(key: String = "book") = LearnedWord(key, key, "sách", "Noun", "A book", "A1", emptyList(), "Read.", "")
    @Test fun `reveal gate duplicate tap and atomic grade survive reopen`() = runBlocking {
        learning.save(1, word(), 100)
        val start = review.start(1, now = 100)
        assertEquals(0, review.start(2, now = 100).total)
        review.grade(1, start.session.id, 0, ReviewGrade.GOOD, 200)
        assertTrue(review.events(1).first().isEmpty())
        review.reveal(1, start.session.id)
        db.close(); open()
        assertTrue(review.start(1, now = 200).session.revealed)
        repeat(2) { review.grade(1, start.session.id, 0, ReviewGrade.GOOD, 200) }
        val event = review.events(1).first().single()
        val operation = db.syncDao().pending(1).last()
        assertEquals("REVIEW_WORD", operation.kind); assertEquals(event.id, operation.operationId)
        assertEquals(200 + ReviewScheduler.DAY_MS, db.reviewDao().card(1, "book")!!.dueAt)
        assertNotNull(review.start(1, start.session.id, 200).session.finishedAt)
    }
    @Test fun `due boundary deletion resave and finite batch`() = runBlocking {
        learning.save(1, word(), 100); learning.save(1, word("cup"), 101)
        assertEquals(1, db.reviewDao().due(1, 100, 20).size)
        val start = review.start(1, now = 102)
        learning.delete(1, start.word!!.word)
        val resumed = review.start(1, now = 102)
        assertEquals(1, resumed.session.position)
        review.reveal(1, resumed.session.id)
        review.grade(1, resumed.session.id, 1, ReviewGrade.AGAIN, 200)
        assertNotNull(review.start(1, resumed.session.id, 200).session.finishedAt)
        assertTrue(db.reviewDao().due(1, 60199, 20).isEmpty())
        assertEquals(1, db.reviewDao().due(1, 60200, 20).size)
        learning.delete(1, resumed.word!!.word); learning.save(1, word(resumed.word.word), 300)
        assertEquals(60200L, db.reviewDao().card(1, resumed.word.word)!!.dueAt)
    }
    @Test fun `migration two to three backfills schedules without losing outbox`() = runBlocking {
        db.close(); context.deleteDatabase("review.db")
        val gson = Gson()
        val schema = gson.fromJson(requireNotNull(javaClass.getResourceAsStream("/com.quocdat.lingolens.data.local.LearningDatabase/2.json")).reader(), JsonObject::class.java)
        val old = context.openOrCreateDatabase("review.db", Context.MODE_PRIVATE, null)
        schema["database"].asJsonObject["entities"].asJsonArray.forEach {
            val entity = it.asJsonObject
            old.execSQL(entity["createSql"].asString.replace("\${TABLE_NAME}", entity["tableName"].asString))
            entity["indices"]?.asJsonArray?.forEach { index -> old.execSQL(index.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", entity["tableName"].asString)) }
        }
        old.execSQL("INSERT INTO SavedWordEntity VALUES (?, ?, ?, ?)", arrayOf<Any>(1, "book", gson.toJson(word()), 100))
        old.execSQL("INSERT INTO OutboxOperation(sequence, operationId, accountId, kind, payload, state, attempts, lastError) VALUES (1, 'pending', 1, 'DELETE_WORD', '{}', 'PENDING', 0, NULL)")
        old.version = 2; old.close(); open()
        assertEquals(100L, db.reviewDao().card(1, "book")!!.dueAt)
        assertNotNull(learning.find(1, "book"))
        assertEquals("pending", db.syncDao().pending(1).single().operationId)
    }
}
