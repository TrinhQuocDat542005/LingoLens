package com.quocdat.lingolens.lesson

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.LearningDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class TypedContentPackRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "typed-content-pack-test.db"
    private val gson = Gson()
    private val fixture = requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs-week7.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
    private val packs = fixture.map { gson.fromJson(it, LessonPack::class.java) }
    private val originalBodies = fixture.map { gson.toJson(it).toByteArray(Charsets.UTF_8) }
    private var bodies = originalBodies.toMutableList()
    private val course = packs[0].courseId
    private lateinit var db: LearningDatabase
    private lateinit var repository: ContentPackRepository
    private var scheduled = 0
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val gateway = object : PackGateway {
        override suspend fun manifest(courseId: String) = PackManifest(1, course, packs.mapIndexed { index, pack ->
            val bytes = bodies[index]
            PackReference(pack.lessonId, pack.revisionId, pack.title, pack.position, pack.contentStatus,
                "/pack", bytes.size.toLong(), hash(bytes))
        })
        override suspend fun download(revisionId: String) = bodies[packs.indexOfFirst { it.revisionId == revisionId }]
    }
    @Before fun setup() {
        context.deleteDatabase(name)
        db = Room.databaseBuilder(context, LearningDatabase::class.java, name).build()
        repository = ContentPackRepository(db, gateway, { it == 1L }, scheduleSync = { scheduled++ })
    }
    @After fun close() {
        db.close()
        context.deleteDatabase(name)
    }

    @Test fun `large typed fixture downloads all three complete lessons with exact types and limits`() = runBlocking {
        repository.downloadCourse(1, course)
        val ready = repository.ready(1, course)
        assertEquals(3, ready.size)
        ready.forEach { pack ->
            assertEquals(10, pack.questions.size)
            assertEquals(6, pack.vocabularies.size)
            assertEquals(setOf("MEANING_CHOICE", "LISTEN_CHOICE", "FILL_GAP"), pack.questions.map { it.type }.toSet())
            assertEquals(2, pack.questions.count { it.type == "LISTEN_CHOICE" })
            assertTrue(pack.questions.all { it.weight == 1 })
            assertTrue(pack.questions.filter { it.type != "FILL_GAP" }.all { q -> q.options.all { it.label.length in 201..500 } })
            assertTrue(pack.questions.filter { it.type == "FILL_GAP" }.all { it.options.isEmpty() &&
                it.answerKey.optionKey.isEmpty() && !it.answerKey.acceptedAnswers.isNullOrEmpty() })
            assertTrue(pack.questions.filter { it.type == "LISTEN_CHOICE" }.all {
                !it.speechText.isNullOrBlank() && !it.fallbackPrompt.isNullOrBlank()
            })
        }
        assertEquals(3, db.lessonDao().observePacks(1).first().size)
        assertTrue(repository.ready(2, course).isEmpty())
        assertEquals(1, scheduled)
    }

    @Test fun `invalid typed metadata is rejected before any row even with matching checksum`() = runBlocking {
        val original = packs[0]
        val listenIndex = original.questions.indexOfFirst { it.type == "LISTEN_CHOICE" }
        val fillIndex = original.questions.indexOfFirst { it.type == "FILL_GAP" }
        val meaningIndex = original.questions.indexOfFirst { it.type == "MEANING_CHOICE" }
        fun changed(index: Int, question: PackedQuestion) =
            original.copy(questions = original.questions.toMutableList().also { it[index] = question })
        val listen = original.questions[listenIndex]
        val fill = original.questions[fillIndex]
        val meaning = original.questions[meaningIndex]
        val invalid = listOf(
            changed(listenIndex, listen.copy(speechText = null)),
            changed(listenIndex, listen.copy(speechText = " ")),
            changed(listenIndex, listen.copy(speechText = "x".repeat(1001))),
            changed(listenIndex, listen.copy(fallbackPrompt = null)),
            changed(listenIndex, listen.copy(fallbackPrompt = " ")),
            changed(listenIndex, listen.copy(fallbackPrompt = "x".repeat(1001))),
            changed(fillIndex, fill.copy(answerKey = PackedAnswerKey("", null))),
            changed(fillIndex, fill.copy(answerKey = PackedAnswerKey("", emptyList()))),
            changed(fillIndex, fill.copy(answerKey = PackedAnswerKey("", listOf("   ")))),
            changed(fillIndex, fill.copy(answerKey = PackedAnswerKey("", listOf("x".repeat(501))))),
            changed(fillIndex, fill.copy(answerKey = PackedAnswerKey("", (1..9).map { "word " + it }))),
            changed(fillIndex, fill.copy(answerKey = PackedAnswerKey("", listOf("word", " WORD ")))),
            changed(fillIndex, fill.copy(answerKey = PackedAnswerKey("A", listOf("word")))),
            changed(fillIndex, fill.copy(options = meaning.options)),
            changed(meaningIndex, meaning.copy(type = "UNKNOWN")),
            changed(meaningIndex, meaning.copy(type = "LISTEN_CHOICE", speechText = "word", fallbackPrompt = "Chọn từ đúng.")),
            changed(meaningIndex, meaning.copy(weight = 2)),
            original.copy(questions = original.questions.dropLast(1))
        )
        invalid.forEachIndexed { index, pack ->
            bodies = originalBodies.toMutableList()
            bodies[0] = gson.toJson(pack).toByteArray(Charsets.UTF_8)
            val rejection = try {
                repository.downloadCourse(1, course)
                null
            } catch (error: IllegalArgumentException) {
                error
            }
            assertNotNull("Invalid metadata case " + index + " must be rejected", rejection)
            assertEquals("Invalid content pack", rejection!!.message)
            assertTrue("No partial or complete row may be written", db.lessonDao().observePacks(1).first().isEmpty())
            assertTrue(repository.ready(1, course).isEmpty())
            assertEquals(0, scheduled)
        }
    }

    @Test fun `legacy choice packs keep old weighted format and absent optional metadata`() {
        val legacy = requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
        legacy.forEach { body ->
            val pack = repository.parse(gson.toJson(body))
            assertTrue(pack.questions.all { it.type == "MEANING_CHOICE" })
            assertTrue(pack.questions.all { it.speechText == null && it.fallbackPrompt == null && it.answerKey.acceptedAnswers == null })
            val weighted = pack.copy(questions = pack.questions.mapIndexed { index, q -> q.copy(weight = if (index == 0) 10 else 1) })
            assertEquals(weighted, repository.parse(gson.toJson(weighted)))
        }
    }
}

