package com.quocdat.lingolens.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.repository.*
import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import com.quocdat.lingolens.model.LearnedWord
import com.quocdat.lingolens.sync.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class SyncEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: LearningDatabase
    private lateinit var learning: LearningRepository
    private val gson = Gson()
    private var signedIn = 1L
    private fun open() {
        db = Room.databaseBuilder(context, LearningDatabase::class.java, "sync-test.db")
            .addMigrations(LearningDatabase.MIGRATION_1_2, LearningDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        learning = LearningRepository(db)
    }
    @Before fun setup() = runBlocking { context.deleteDatabase("sync-test.db"); open(); db.syncDao().saveAccount(SyncAccount(1, true)) }
    @After fun close() { db.close(); context.deleteDatabase("sync-test.db") }
    private fun word() = LearnedWord("book", "book", "sách", "Noun", "A book", "A1", emptyList(), "Read.", "")
    private class FakeApi : SyncApi {
        val calls = mutableListOf<SyncRequest>()
        val effects = mutableSetOf<String>()
        var error: Exception? = null
        var loseResponse = false
        var snapshot: List<CloudEntry> = emptyList()
        var reviews = ReviewSnapshot(emptyList(), emptyList())
        override suspend fun reviews(account: Long, accountId: Long) = ApiEnvelope(true, "ok", reviews)
        var duringPull: (suspend () -> Unit)? = null
        override suspend fun apply(account: Long, request: SyncRequest): ApiEnvelope<SyncReceipt> {
            calls += request
            error?.let { throw it }
            effects += request.operationId
            if (loseResponse) { loseResponse = false; throw IOException("Response lost after commit") }
            return ApiEnvelope(true, "ok", SyncReceipt(request.operationId, 1))
        }
        override suspend fun snapshot(account: Long, accountId: Long): ApiEnvelope<List<CloudEntry>> {
            duringPull?.invoke(); return ApiEnvelope(true, "ok", snapshot)
        }
    }
    private fun engine(api: SyncApi) = SyncEngine(db, api, { it == signedIn })
    @Test fun `review pull restores schedule and deduplicates history`() = runBlocking {
        val api = FakeApi().apply {
            snapshot = listOf(CloudEntry("book", gson.toJson(word()), 100, 100, false))
            reviews = ReviewSnapshot(listOf(CloudReviewCard("book", 90000, 1, 2.5, 1, 0, "REVIEW", 200)), listOf(CloudReviewEvent("event", "book", "GOOD", 200)))
        }
        repeat(2) { assertEquals(SyncOutcome.COMPLETE, engine(api).run(1)) }
        assertEquals(90000L, db.reviewDao().card(1, "book")!!.dueAt)
        assertEquals(1, db.reviewDao().events(1).first().size)
        assertTrue(db.reviewDao().events(2).first().isEmpty())
    }
    @Test fun `offline grade made during pull protects its schedule and history`() = runBlocking {
        learning.save(1, word(), 100)
        val repository = ReviewRepository(db)
        val session = repository.start(1, now = 100)
        repository.reveal(1, session.session.id)
        val api = FakeApi().apply {
            snapshot = listOf(CloudEntry("book", gson.toJson(word()), 100, 100, false))
            reviews = ReviewSnapshot(listOf(CloudReviewCard("book", 100, 0, 2.5, 0, 0, "NEW", null)), emptyList())
            duringPull = { repository.grade(1, session.session.id, 0, com.quocdat.lingolens.review.core.ReviewGrade.EASY, 200) }
        }
        assertEquals(SyncOutcome.COMPLETE, engine(api).run(1))
        assertEquals(4, db.reviewDao().card(1, "book")!!.intervalDays)
        assertEquals("REVIEW_WORD", db.syncDao().pending(1).single().kind)
        assertEquals(1, db.reviewDao().events(1).first().size)
    }
    @Test fun `local edit and durable queue survive reopen with FIFO order`() = runBlocking {
        learning.save(1, word()); learning.delete(1, "book")
        val before = db.syncDao().pending(1).map { it.operationId }
        db.close(); open()
        val api = FakeApi()
        assertEquals(SyncOutcome.COMPLETE, engine(api).run(1))
        assertEquals(before, api.calls.map { it.operationId })
        assertTrue(db.syncDao().pending(1).isEmpty()); assertNull(learning.find(1, "book"))
    }
    @Test fun `lost acknowledgement retries the identical ID without duplicate effect`() = runBlocking {
        learning.save(1, word()); val api = FakeApi().apply { loseResponse = true }
        assertEquals(SyncOutcome.RETRY, engine(api).run(1))
        assertEquals(1, db.syncDao().pending(1).single().attempts)
        assertEquals(SyncOutcome.COMPLETE, engine(api).run(1))
        assertEquals(api.calls[0].operationId, api.calls[1].operationId)
        assertEquals(1, api.effects.size)
    }
    @Test fun `signed out or different account cannot drain queue`() = runBlocking {
        learning.save(1, word()); val api = FakeApi(); signedIn = 2
        assertEquals(SyncOutcome.PAUSED, engine(api).run(1))
        assertTrue(api.calls.isEmpty()); assertEquals(1, db.syncDao().pending(1).size)
    }
    @Test fun `transient failures retry while validation failures block and remain visible`() = runBlocking {
        learning.save(1, word()); val api = FakeApi().apply { error = HttpException(Response.error<Any>(503, "offline".toResponseBody())) }
        assertEquals(SyncOutcome.RETRY, engine(api).run(1))
        api.error = HttpException(Response.error<Any>(400, "invalid".toResponseBody()))
        assertEquals(SyncOutcome.BLOCKED, engine(api).run(1))
        assertEquals("BLOCKED", db.syncDao().pending(1).single().state)
        api.error = null; assertEquals(SyncOutcome.BLOCKED, engine(api).run(1))
        db.syncDao().retry(1); assertEquals(SyncOutcome.COMPLETE, engine(api).run(1))
    }
    @Test fun `cloud snapshot imports another device but preserves an edit made during download`() = runBlocking {
        val cloud = word()
        val api = FakeApi().apply {
            snapshot = listOf(CloudEntry("book", gson.toJson(cloud), 100, 50, false))
            duringPull = { learning.delete(1, "book") }
        }
        assertEquals(SyncOutcome.COMPLETE, engine(api).run(1))
        assertNull(learning.find(1, "book")); assertEquals("DELETE_WORD", db.syncDao().pending(1).single().kind)
        api.duringPull = null; api.snapshot = listOf(CloudEntry("book", gson.toJson(cloud), 100, 50, false))
        // A fresh account has no local edits, so it receives its cloud snapshot.
        signedIn = 2; db.syncDao().saveAccount(SyncAccount(2, true))
        assertEquals(SyncOutcome.COMPLETE, engine(api).run(2)); assertNotNull(learning.find(2, "book"))
        assertEquals(50L, db.learningDao().allEvents(2).single().occurredAt)
    }
    @Test fun `bootstrap queues existing week 2 data once including deleted learning history`() = runBlocking {
        db.learningDao().save(SavedWordEntity(3, "book", gson.toJson(word()), 100))
        db.learningDao().record(LearningEvent(3, "book", 50))
        db.learningDao().record(LearningEvent(3, "deleted-word", 40))
        val engine = engine(FakeApi()); engine.bootstrap(3)
        val ids = db.syncDao().pending(3).map { it.operationId }
        engine.bootstrap(3)
        assertEquals(ids, db.syncDao().pending(3).map { it.operationId })
        assertEquals(3, ids.size)
    }
    @Test fun `migration from exported v1 schema preserves saved words and events`() = runBlocking {
        db.close(); context.deleteDatabase("sync-test.db")
        val old = context.openOrCreateDatabase("sync-test.db", Context.MODE_PRIVATE, null)
        val schema = gson.fromJson(requireNotNull(javaClass.getResourceAsStream("/com.quocdat.lingolens.data.local.LearningDatabase/1.json")).reader(), JsonObject::class.java)
        schema.getAsJsonObject("database").getAsJsonArray("entities").forEach {
            val entity = it.asJsonObject
            old.execSQL(entity["createSql"].asString.replace("\${TABLE_NAME}", entity["tableName"].asString))
        }
        old.execSQL("INSERT INTO SavedWordEntity VALUES (?, ?, ?, ?)", arrayOf<Any>(1, "book", gson.toJson(word()), 100))
        old.execSQL("INSERT INTO LearningEvent VALUES (?, ?, ?)", arrayOf<Any>(1, "book", 50))
        old.version = 1; old.close(); open()
        assertNotNull(learning.find(1, "book")); assertEquals(50L, db.learningDao().allEvents(1).single().occurredAt)
        assertTrue(db.syncDao().pending(1).isEmpty())
    }
}
