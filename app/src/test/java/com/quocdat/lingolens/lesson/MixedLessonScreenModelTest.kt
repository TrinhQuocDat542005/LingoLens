package com.quocdat.lingolens.lesson

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.*
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MixedLessonScreenModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "mixed-lesson-ui.db"
    private val gson = Gson()
    private val packs = requireNotNull(javaClass.getResourceAsStream("/curriculum/large-packs-week7.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonArray }.map { gson.fromJson(it, LessonPack::class.java) }
    private lateinit var db: LearningDatabase
    private lateinit var repository: LessonStudyRepository
    private val scopes = mutableListOf<CoroutineScope>()
    private fun bytes(pack: LessonPack) = gson.toJson(pack).toByteArray(Charsets.UTF_8)
    private fun correct(question: PackedQuestion) =
        if (question.type == "FILL_GAP") question.answerKey.acceptedAnswers!!.first() else question.answerKey.optionKey
    private fun open() {
        db = Room.databaseBuilder(context, LearningDatabase::class.java, name).build()
        val packRepository = ContentPackRepository(db, object : PackGateway {
            override suspend fun manifest(courseId: String) = PackManifest(1, courseId, packs.map { pack ->
                val data = bytes(pack)
                PackReference(pack.lessonId, pack.revisionId, pack.title, pack.position, pack.contentStatus, "/pack",
                    data.size.toLong(), MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) })
            })
            override suspend fun download(revisionId: String) = bytes(packs.first { it.revisionId == revisionId })
        }, { it == 1L })
        val sessions = LessonSessionRepository(db, packRepository, { it == 1L })
        val gateway = object : StudyGateway {
            override suspend fun catalog(account: Long) = CatalogDto(listOf(CourseDto(packs[0].courseId,
                "DEMO typed", "DEMO", packs[0].lessonId, packs[0].revisionId, false)), 0)
            override suspend fun enroll(account: Long, course: String) = Unit
            override suspend fun snapshot(account: Long, course: String) = LessonCourseSnapshot(course, "ACTIVE", emptyList(), 0)
        }
        repository = LessonStudyRepository(db, packRepository, sessions, gateway, { it == 1L }, {})
    }
    private fun model(): OfflineLessonScreenModel {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        return OfflineLessonScreenModel(SavedStateHandle(), repository, 1, { true }, scope)
    }
    private suspend fun stopProcess() {
        val jobs = scopes.map { requireNotNull(it.coroutineContext[Job]) }
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }
        assertTrue("Old model jobs must finish before Room reopens", jobs.all { it.isCompleted && it.children.none() })
        scopes.clear()
        db.close()
        assertFalse("Old Room instance must be closed", db.isOpen)
    }
    @Before fun setup() { context.deleteDatabase(name); open() }
    @After fun close() = runBlocking { stopProcess(); context.deleteDatabase(name); Unit }
    private suspend fun prepare(model: OfflineLessonScreenModel) {
        model.load(); model.download(packs[0].courseId); model.openLesson(packs[0].courseId, packs[0].lessonId)
        model.showQuestions()
    }
    private suspend fun goTo(model: OfflineLessonScreenModel, question: PackedQuestion) {
        while (model.state.value.questionIndex < question.position - 1) {
            val current = packs[0].questions[model.state.value.questionIndex]
            model.choose(current.id, correct(current)); model.next()
        }
    }
    @Test fun `fill edits preserve exact raw input and UUID through database reopen`() = runBlocking {
        val model = model(); prepare(model)
        val fill = packs[0].questions.first { it.type == "FILL_GAP" }; goTo(model, fill)
        val id = model.state.value.session!!.id
        val raw = "  " + correct(fill).uppercase() + "  "
        model.choose(fill.id, raw); model.checkAnswer()
        assertEquals(raw, model.state.value.answers[fill.id])
        assertTrue(model.state.value.feedback!!.correct)
        assertEquals(fill.explanation, model.state.value.feedback!!.explanation)
        val position = model.state.value.questionIndex
        stopProcess(); open()
        val restored = model(); restored.load(); restored.openLesson(packs[0].courseId, packs[0].lessonId)
        assertEquals(id, restored.state.value.session!!.id)
        assertEquals(raw, restored.state.value.answers[fill.id])
        assertEquals(position, restored.state.value.questionIndex)
        packs[0].questions.forEach { restored.choose(it.id, if (it.id == fill.id) raw else correct(it)) }
        restored.submit()
        val payload = gson.fromJson(db.lessonDao().outbox(1, id)!!.payloadJson, AttemptRequestDto::class.java)
        assertEquals(raw, payload.answers.first { it.questionId == fill.id }.optionKey)
        assertEquals(10, restored.state.value.correctCount)
    }
    @Test fun `rapid fill writes are serialized rather than dropped while busy`() = runBlocking {
        val model = model(); prepare(model)
        val fill = packs[0].questions.first { it.type == "FILL_GAP" }; goTo(model, fill)
        val edits = listOf("a", "ap", "app", "apple", "  APPLE  ")
        coroutineScope { edits.map { value -> launch { model.choose(fill.id, value) } }.joinAll() }
        assertEquals(edits.last(), model.state.value.answers[fill.id])
        val stored = JsonParser.parseString(db.lessonDao().session(1, model.state.value.session!!.id)!!.answersJson).asJsonObject
        assertEquals(edits.last(), stored[fill.id].asString)
        assertFalse(model.state.value.busy)
    }
    @Test fun `blank fill cannot advance or submit and wrong spelling has explicit feedback`() = runBlocking {
        val model = model(); prepare(model)
        val fill = packs[0].questions.first { it.type == "FILL_GAP" }; goTo(model, fill)
        model.choose(fill.id, " \t ")
        val position = model.state.value.questionIndex
        model.next(); assertEquals(position, model.state.value.questionIndex)
        packs[0].questions.filter { it.id != fill.id }.forEach { model.choose(it.id, correct(it)) }
        model.submit(); assertNotNull(model.state.value.error)
        assertNull(model.state.value.session!!.frozenPayloadJson)
        assertTrue(db.lessonDao().pending(1).isEmpty())
        model.choose(fill.id, "this spelling is deliberately wrong"); model.checkAnswer()
        assertFalse(model.state.value.feedback!!.correct)
        assertEquals(fill.explanation, model.state.value.feedback!!.explanation)
    }
    @Test fun `listening fallback keeps same question options answer count and queued payload`() = runBlocking {
        val model = model(); prepare(model)
        val listen = packs[0].questions.first { it.type == "LISTEN_CHOICE" }; goTo(model, listen)
        model.useFallback(listen.id)
        assertTrue(listen.id in model.state.value.fallbackQuestions)
        assertEquals(listen.id, model.state.value.pack!!.questions[model.state.value.questionIndex].id)
        assertEquals(10, model.state.value.questionCount)
        model.choose(listen.id, correct(listen))
        assertTrue(model.state.value.feedback!!.correct)
        packs[0].questions.forEach { model.choose(it.id, correct(it)) }; model.submit()
        val queued = db.lessonDao().pending(1).single()
        val payload = gson.fromJson(queued.payloadJson, AttemptRequestDto::class.java)
        assertEquals(10, payload.answers.size)
        assertEquals(correct(listen), payload.answers.single { it.questionId == listen.id }.optionKey)
        assertEquals(10, model.state.value.correctCount)
        assertEquals(0, model.state.value.officialXp)
    }
    @Test fun `local wrong practice changes no durable row UUID payload counts progress or XP`() = runBlocking {
        val model = model(); prepare(model)
        val fill = packs[0].questions.first { it.type == "FILL_GAP" }
        packs[0].questions.forEach { model.choose(it.id, if (it.id == fill.id) "wrong answer" else correct(it)) }
        model.submit()
        val beforeSession = db.lessonDao().sessions(1)
        val beforeQueue = db.lessonDao().pending(1)
        val beforeCurriculum = db.lessonDao().curriculum(1, packs[0].courseId)
        val beforePacks = db.lessonDao().completePacks(1, packs[0].courseId)
        val originalCorrect = model.state.value.correctCount
        model.startWrongPractice()
        assertEquals("RETRY_WRONG", model.state.value.stage)
        assertEquals(listOf(fill.id), model.state.value.localRetry!!.questionIds)
        model.chooseRetry(fill.id, correct(fill)); model.checkRetry()
        assertTrue(model.state.value.localRetry!!.feedback!!.correct)
        model.nextRetry()
        assertEquals("SUMMARY", model.state.value.stage)
        assertEquals(originalCorrect, model.state.value.correctCount)
        assertEquals(9, originalCorrect)
        assertEquals(beforeSession, db.lessonDao().sessions(1))
        assertEquals(beforeQueue, db.lessonDao().pending(1))
        assertEquals(beforeCurriculum, db.lessonDao().curriculum(1, packs[0].courseId))
        assertEquals(beforePacks, db.lessonDao().completePacks(1, packs[0].courseId))
        assertEquals(0, model.state.value.officialXp)
        assertEquals("wrong answer", model.state.value.answers[fill.id])
    }
    @Test fun `feedback uses shared per question marks and local practice cannot start before submit`() = runBlocking {
        val model = model(); prepare(model)
        model.startWrongPractice(); assertNull(model.state.value.localRetry)
        val question = packs[0].questions.first()
        val answer = correct(question)
        model.choose(question.id, answer); model.checkAnswer()
        val shared = PackGrader.mark(question, SubmittedAnswer(UUID.fromString(question.id), answer))
        assertEquals(shared.correct, model.state.value.feedback!!.correct)
        assertEquals(question.explanation, model.state.value.feedback!!.explanation)
        assertEquals("ANSWERING", db.lessonDao().session(1, model.state.value.session!!.id)!!.phase)
    }
    @Test fun `all three typed lessons can queue offline with shared grades and no official XP`() = runBlocking {
        val model = model(); prepare(model)
        for (pack in packs) {
            model.openLesson(pack.courseId, pack.lessonId); model.showQuestions()
            pack.questions.forEach { question ->
                if (question.type == "LISTEN_CHOICE") model.useFallback(question.id)
                model.choose(question.id, correct(question)); model.next()
            }
            model.submit()
            assertEquals("QUEUED", model.state.value.session!!.phase)
            assertEquals(10, model.state.value.correctCount)
            assertEquals(10, model.state.value.questionCount)
            assertEquals(0, model.state.value.officialXp)
            assertNull(model.state.value.confirmedResult)
        }
        assertEquals(3, db.lessonDao().pending(1).size)
        assertTrue(db.lessonDao().pending(1).all { gson.fromJson(it.payloadJson, AttemptRequestDto::class.java).submittedOffline })
    }
}
