package com.quocdat.lingolens.lesson

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class TypedLessonSessionRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "typed-durable-lesson-test.db"
    private val gson = Gson()
    private val packs = requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs-week7.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonArray }.map { gson.fromJson(it, LessonPack::class.java) }
    private var served = packs
    private var signedIn = 1L
    private var time = 1000L
    private var scheduled = 0
    private var failAfterEnqueue = false
    private lateinit var db: LearningDatabase
    private lateinit var packRepository: ContentPackRepository
    private lateinit var repository: LessonSessionRepository
    private val course = packs[0].courseId
    private fun bytes(pack: LessonPack) = gson.toJson(pack).toByteArray(Charsets.UTF_8)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val gateway = object : PackGateway {
        override suspend fun manifest(courseId: String) = PackManifest(1, course, served.map { pack ->
            val body = bytes(pack)
            PackReference(pack.lessonId, pack.revisionId, pack.title, pack.position, pack.contentStatus,
                "/pack", body.size.toLong(), hash(body))
        })
        override suspend fun download(revisionId: String) = bytes(served.first { it.revisionId == revisionId })
    }
    private fun open() {
        db = Room.databaseBuilder(context, LearningDatabase::class.java, name).build()
        packRepository = ContentPackRepository(db, gateway, { signedIn == it })
        repository = LessonSessionRepository(db, packRepository, { signedIn == it }, clock = { time },
            scheduleSync = { scheduled++ }, afterEnqueue = { if (failAfterEnqueue) error("injected failure after enqueue") })
    }
    @Before fun setup() = runBlocking {
        context.deleteDatabase(name)
        open()
        for (account in 1L..2L) {
            signedIn = account
            packRepository.downloadCourse(account, course)
            db.lessonDao().saveCurriculum(CurriculumAccountState(account, course, "ACTIVE", "[]", 0))
        }
        signedIn = 1L
    }
    @After fun close() {
        db.close()
        context.deleteDatabase(name)
    }
    private fun correctInput(question: PackedQuestion) = if (question.type == "FILL_GAP")
        " \u00a0" + requireNotNull(question.answerKey.acceptedAnswers).first().uppercase(Locale.ROOT) + " \t "
    else question.answerKey.optionKey
    private suspend fun answerAll(session: LessonSession) {
        packs.first { it.revisionId == session.revisionId }.questions.forEachIndexed { index, question ->
            repository.answer(1, session.id, question.id, correctInput(question), index)
        }
    }
    private fun storedAnswers(session: LessonSession) = JsonParser.parseString(session.answersJson).asJsonObject
        .entrySet().associate { it.key to it.value.asString }

    @Test fun `raw fill input UUID revision and position survive file database close reopen`() = runBlocking {
        val session = repository.start(1, packs[0].revisionId)
        val fill = packs[0].questions.first { it.type == "FILL_GAP" }
        val raw = correctInput(fill)
        repository.answer(1, session.id, fill.id, raw, fill.position - 1)
        val before = repository.resume(1, session.id)
        assertEquals(raw, storedAnswers(before)[fill.id])
        assertNotEquals(LessonGrader.normalizeFill(raw), raw)
        db.close()
        assertFalse(db.isOpen)
        open()
        val after = repository.resume(1, session.id)
        assertEquals(before, after)
        assertEquals(session.id, after.id)
        assertEquals(packs[0].revisionId, after.revisionId)
        assertEquals(fill.position - 1, after.position)
        assertEquals(raw, storedAnswers(after)[fill.id])
        assertNull(after.frozenPayloadJson)
        assertTrue(db.lessonDao().pending(1).isEmpty())
    }

    @Test fun `blank fill draft persists but submit rejects before frozen session or outbox`() = runBlocking {
        val session = repository.start(1, packs[0].revisionId)
        answerAll(session)
        val fill = packs[0].questions.first { it.type == "FILL_GAP" }
        val blank = " \u00a0\t "
        repository.answer(1, session.id, fill.id, blank, fill.position - 1)
        val before = repository.resume(1, session.id)
        assertEquals(blank, storedAnswers(before)[fill.id])
        val rejection = try { repository.submit(1, session.id, true); null } catch (error: IllegalArgumentException) { error }
        assertNotNull(rejection)
        assertEquals(before, repository.resume(1, session.id))
        assertNull(repository.resume(1, session.id).frozenPayloadJson)
        assertTrue(db.lessonDao().pending(1).isEmpty())
        assertEquals(0, scheduled)
        assertEquals(0, db.lessonDao().curriculum(1, course)!!.confirmedXpTotal)
    }

    @Test fun `mixed answers freeze raw payload and outbox atomically and retries keep UUID bytes`() = runBlocking {
        val session = repository.start(1, packs[0].revisionId)
        answerAll(session)
        val raw = storedAnswers(repository.resume(1, session.id))
        val grade = repository.provisionalGrade(1, session.id)
        assertEquals(10, grade.correctCount)
        assertTrue(grade.passed)
        val payload = repository.submit(1, session.id, true)
        val queued = db.lessonDao().pending(1).single()
        val frozen = repository.resume(1, session.id)
        assertEquals(session.id, queued.attemptId)
        assertEquals(session.id, queued.sessionId)
        assertEquals(payload, queued.payloadJson)
        assertEquals(hash(payload.toByteArray(Charsets.UTF_8)), queued.payloadHash)
        assertEquals(payload, frozen.frozenPayloadJson)
        assertEquals("QUEUED", frozen.phase)
        val request = gson.fromJson(payload, AttemptRequestDto::class.java)
        assertEquals(session.id, request.id)
        assertEquals(packs[0].revisionId, request.lessonRevisionId)
        assertEquals(raw, request.answers.associate { it.questionId to it.optionKey })
        assertEquals(1000L, request.clientCompletedAt)
        assertTrue(request.submittedOffline)
        assertEquals(1, scheduled)
        time = 9000L
        assertEquals(payload, repository.submit(1, session.id, false))
        assertEquals(queued, db.lessonDao().pending(1).single())
        db.close()
        assertFalse(db.isOpen)
        open()
        assertEquals(payload, repository.submit(1, session.id, false))
        assertEquals(queued, db.lessonDao().pending(1).single())
        val fill = packs[0].questions.first { it.type == "FILL_GAP" }
        val rejection = try { repository.answer(1, session.id, fill.id, "changed", 0); null }
            catch (error: IllegalStateException) { error }
        assertNotNull(rejection)
        assertEquals(frozen, repository.resume(1, session.id))
        assertEquals(0, db.lessonDao().curriculum(1, course)!!.confirmedXpTotal)
    }

    @Test fun `mixed enqueue failure rolls back frozen payload and queue together`() = runBlocking {
        val session = repository.start(1, packs[0].revisionId)
        answerAll(session)
        val before = repository.resume(1, session.id)
        failAfterEnqueue = true
        val rejection = try { repository.submit(1, session.id, true); null } catch (error: IllegalStateException) { error }
        assertNotNull(rejection)
        assertEquals(before, repository.resume(1, session.id))
        assertTrue(db.lessonDao().pending(1).isEmpty())
        assertEquals(0, scheduled)
        failAfterEnqueue = false
        repository.submit(1, session.id, true)
        assertEquals(1, db.lessonDao().pending(1).size)
    }

    @Test fun `foreign account cannot read edit or submit another account mixed queue`() = runBlocking {
        val session = repository.start(1, packs[0].revisionId)
        answerAll(session)
        repository.submit(1, session.id, true)
        val queued = db.lessonDao().pending(1).single()
        val before = repository.resume(1, session.id)
        signedIn = 2
        val readError = try { repository.resume(2, session.id); null } catch (error: IllegalStateException) { error }
        assertNotNull(readError)
        val editError = try { repository.answer(2, session.id, packs[0].questions[0].id, "A", 0); null }
            catch (error: IllegalStateException) { error }
        assertNotNull(editError)
        val submitError = try { repository.submit(2, session.id, true); null } catch (error: IllegalStateException) { error }
        assertNotNull(submitError)
        assertTrue(db.lessonDao().pending(2).isEmpty())
        assertEquals(queued, db.lessonDao().pending(1).single())
        assertEquals(before, db.lessonDao().session(1, session.id))
        signedIn = 0
        val logoutError = try { repository.resume(1, session.id); null } catch (error: IllegalStateException) { error }
        assertNotNull(logoutError)
        signedIn = 1
        assertEquals(before, repository.resume(1, session.id))
    }

    @Test fun `new manifest keeps old session revision snapshot and grader answers`() = runBlocking {
        val session = repository.start(1, packs[0].revisionId)
        answerAll(session)
        val before = repository.resume(1, session.id)
        val oldPack = db.lessonDao().pack(1, session.revisionId)!!
        val fillIndex = packs[0].questions.indexOfFirst { it.type == "FILL_GAP" }
        val questions = packs[0].questions.mapIndexed { index, question ->
            question.copy(id = UUID.randomUUID().toString(),
                answerKey = if (index == fillIndex) PackedAnswerKey("", listOf("different approved word")) else question.answerKey)
        }
        val replacement = packs[0].copy(revisionId = UUID.randomUUID().toString(), questions = questions)
        served = listOf(replacement) + packs.drop(1)
        val latest = gateway.manifest(course)
        val body = bytes(replacement)
        // Explicit monotonic timestamp simulates a completed later download without sleeps.
        val later = db.lessonDao().completePacks(1, course).maxOf { it.downloadedAt } + 1
        db.lessonDao().insertPack(ContentPack(1, replacement.revisionId, replacement.lessonId, course, 1,
            gson.toJson(latest), String(body, Charsets.UTF_8), hash(body), body.size.toLong(), later, true))
        assertEquals(replacement.revisionId, packRepository.ready(1, course)[0].revisionId)
        assertEquals(oldPack, db.lessonDao().pack(1, session.revisionId))
        assertEquals(before, repository.resume(1, session.id))
        assertEquals(10, repository.provisionalGrade(1, session.id).correctCount)
        val oldAnswers = storedAnswers(before)
        val newAnswers = replacement.questions.mapIndexed { index, question ->
            SubmittedAnswer(UUID.fromString(question.id), oldAnswers.getValue(packs[0].questions[index].id))
        }
        assertEquals(9, PackGrader.grade(replacement.questions, newAnswers).correctCount)
        val payload = repository.submit(1, session.id, true)
        assertEquals(session.revisionId, gson.fromJson(payload, AttemptRequestDto::class.java).lessonRevisionId)
        db.close()
        assertFalse(db.isOpen)
        open()
        assertEquals(session.revisionId, repository.resume(1, session.id).revisionId)
        assertEquals(payload, repository.resume(1, session.id).frozenPayloadJson)
        assertEquals(10, repository.provisionalGrade(1, session.id).correctCount)
    }

    @Test fun `invalid fill length choice key question and position do not mutate draft`() = runBlocking {
        val session = repository.start(1, packs[0].revisionId)
        val before = repository.resume(1, session.id)
        val fill = packs[0].questions.first { it.type == "FILL_GAP" }
        val choice = packs[0].questions.first { it.type == "MEANING_CHOICE" }
        val invalid = listOf(Triple(fill.id, "x".repeat(501), 0), Triple(choice.id, "INVALID", 0),
            Triple("not-a-question", "word", 0), Triple(fill.id, "word", -1))
        invalid.forEach { (question, raw, position) ->
            val rejection = try { repository.answer(1, session.id, question, raw, position); null }
                catch (error: IllegalArgumentException) { error }
            assertNotNull(rejection)
            assertEquals(before, repository.resume(1, session.id))
            assertTrue(db.lessonDao().pending(1).isEmpty())
        }
    }
}

