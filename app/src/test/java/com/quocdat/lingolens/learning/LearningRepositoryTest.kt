package com.quocdat.lingolens.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.repository.*
import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import com.quocdat.lingolens.model.LearnedWord
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LearningRepositoryTest {
    private lateinit var db: LearningDatabase
    private lateinit var repository: LearningRepository
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun open() {
        db = Room.databaseBuilder(context, LearningDatabase::class.java, "test-learning.db").allowMainThreadQueries().build()
        repository = LearningRepository(db)
    }
    @Before fun setup() { context.deleteDatabase("test-learning.db"); open() }
    @After fun close() { db.close(); context.deleteDatabase("test-learning.db") }
    private fun word(name: String = "book") = LearnedWord(name, name, "sách", "Noun", "A book", "A1", emptyList(), "Read a book.", "")
    @Test fun `words survive database reopen and stay account scoped`() = runBlocking {
        repository.save(1, word(), 100)
        repository.save(2, word("cup"), 200)
        db.close(); open()
        assertEquals(listOf("book"), repository.words(1).first().map { it.word })
        assertNull(repository.find(2, "book"))
        repository.delete(2, "book")
        assertNotNull(repository.find(1, "book"))
    }
    @Test fun `normalization deduplicates and deleting resaving preserves first learning event`() = runBlocking {
        repository.save(1, word(" Book "), 100)
        repository.save(1, word("BOOK"), 200)
        assertEquals(1, repository.words(1).first().size)
        assertEquals(100L, repository.words(1).first().single().timestamp)
        repository.delete(1, "book")
        repository.save(1, word(), 300)
        assertEquals(100L, repository.events(1).first().single().occurredAt)
        assertEquals(300L, repository.words(1).first().single().timestamp)
    }
    private class TestApi : VocabularyApi {
        var error: Exception? = null
        var calls = 0
        var failSecondPage = false
        val entry = PublishedVocabulary(1, "book", "sách", "/bʊk/", "Noun", "A book", "A1", "Read.", "", emptyList())
        override suspend fun lookup(word: String): ApiEnvelope<PublishedVocabulary> {
            calls++; error?.let { throw it }; return ApiEnvelope(true, "ok", entry)
        }
        override suspend fun list(page: Int, size: Int): ApiEnvelope<VocabularyPage> {
            if (failSecondPage && page == 1) throw IOException("offline")
            return ApiEnvelope(true, "ok", VocabularyPage(listOf(entry), if (failSecondPage) 2 else 1))
        }
    }
    @Test fun `cache works offline but refresh 404 evicts unpublished catalog content`() = runBlocking {
        val api = TestApi(); val vocabulary = VocabularyRepository(api, db, repository)
        vocabulary.refreshCatalog()
        api.error = IOException("offline")
        assertNotNull(vocabulary.lookup(1, "book").word); assertEquals(0, api.calls)
        assertNotNull(vocabulary.lookup(1, "book", refresh = true).word)
        api.error = HttpException(Response.error<Any>(404, "missing".toResponseBody()))
        assertNull(vocabulary.lookup(1, "book", refresh = true).word)
        assertNull(db.learningDao().cached("book"))
    }
    @Test fun `saved snapshots do not leak to other accounts and partial catalog refresh keeps previous cache`() = runBlocking {
        val api = TestApi(); val vocabulary = VocabularyRepository(api, db, repository)
        repository.save(1, word("private-word"))
        api.error = IOException("offline")
        assertNotNull(vocabulary.lookup(1, "private-word").word)
        assertNull(vocabulary.lookup(2, "private-word").word)
        vocabulary.refreshCatalog(); api.failSecondPage = true
        try { vocabulary.refreshCatalog(); fail("Expected network failure") } catch (_: IOException) {}
        assertNotNull(db.learningDao().cached("book"))
    }
    @Test fun `authentication failure is never masked as cached vocabulary success`() = runBlocking {
        val api = TestApi(); val vocabulary = VocabularyRepository(api, db, repository)
        vocabulary.refreshCatalog()
        api.error = HttpException(Response.error<Any>(401, "unauthorized".toResponseBody()))
        try { vocabulary.lookup(1, "book", refresh = true); fail("Expected authentication error") } catch (ex: HttpException) { assertEquals(401, ex.code()) }
    }
}
