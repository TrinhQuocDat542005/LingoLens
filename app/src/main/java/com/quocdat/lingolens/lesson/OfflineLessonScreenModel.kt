package com.quocdat.lingolens.lesson

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.LessonSession
import com.quocdat.lingolens.data.remote.toUserMessage
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LessonQuestionFeedback(val questionId: String, val correct: Boolean, val explanation: String)
data class LocalWrongPractice(val questionIds: List<String>, val index: Int = 0,
    val answers: Map<String, String> = emptyMap(), val feedback: LessonQuestionFeedback? = null)

data class OfflineLessonUiState(val courses: List<StudyCourse> = emptyList(), val session: LessonSession? = null,
    val pack: LessonPack? = null, val stage: String = "CATALOG", val questionIndex: Int = 0,
    val answers: Map<String, String> = emptyMap(), val correctCount: Int? = null, val questionCount: Int = 0,
    val passed: Boolean? = null, val confirmedResult: LessonSyncResult? = null, val officialXp: Int = 0,
    val syncError: String? = null, val busy: Boolean = false, val error: String? = null,
    val feedback: LessonQuestionFeedback? = null, val fallbackQuestions: Set<String> = emptySet(),
    val wrongQuestionIds: List<String> = emptyList(), val localRetry: LocalWrongPractice? = null) {
    val frozen get() = session?.frozenPayloadJson != null
    val canRetryWrong get() = frozen && wrongQuestionIds.isNotEmpty()
}

class OfflineLessonScreenModel(private val saved: SavedStateHandle, private val repository: LessonStudyRepository,
    private val account: Long, private val isOffline: () -> Boolean, scope: CoroutineScope? = null) : ViewModel() {
    companion object {
        fun factory(repository: LessonStudyRepository, account: Long, isOffline: () -> Boolean): ViewModelProvider.Factory =
            viewModelFactory { initializer { OfflineLessonScreenModel(createSavedStateHandle(), repository, account, isOffline) } }
    }
    private val gson = Gson()
    private val mutable = MutableStateFlow(OfflineLessonUiState())
    val state: StateFlow<OfflineLessonUiState> = mutable
    private val refreshMutex = Mutex()
    // Every raw edit waits its turn. The former busy early-return silently discarded fast input.
    private val actionMutex = Mutex()
    init {
        if (saved.get<Long>("durableAccount") != account) saved.keys().toList().forEach { saved.remove<Any>(it) }
        saved["durableAccount"] = account
        (scope ?: viewModelScope).launch {
            try { repository.observe(account).collect { refreshLocal() } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.value = mutable.value.copy(error = error.toUserMessage(gson)) }
        }
    }

    fun hasAnswer(question: PackedQuestion, value: String? = state.value.answers[question.id]): Boolean = when (question.type) {
        "FILL_GAP" -> value != null && LessonGrader.normalizeFill(value).isNotBlank()
        else -> value != null && question.options.any { it.key == value }
    }

    suspend fun refreshLocal() = refreshMutex.withLock {
        val snapshot = repository.read(account)
        if (!repository.signedIn(account)) {
            saved.remove<String>("session"); saved.remove<String>("view"); saved.remove<ArrayList<String>>("fallbackQuestions")
            mutable.value = OfflineLessonUiState(error = "Phiên đăng nhập đã thay đổi.")
            return@withLock
        }
        val previous = mutable.value
        val selected = snapshot.sessions.firstOrNull { it.id == saved.get<String>("session") }
        val pack = selected?.let { repository.pack(account, it) }
        val answers: Map<String, String> = selected?.let {
            gson.fromJson(it.answersJson, object : TypeToken<Map<String, String>>() {}.type)
        } ?: emptyMap()
        val confirmed = selected?.takeIf { it.phase == "CONFIRMED" }?.serverResultJson?.let {
            gson.fromJson(it, LessonSyncResult::class.java)
        }
        val complete = pack != null && pack.questions.all { hasAnswer(it, answers[it.id]) }
        val grade = if (complete) PackGrader.grade(pack!!.questions,
            answers.map { SubmittedAnswer(UUID.fromString(it.key), it.value) }) else null
        val sameSession = selected != null && selected.id == previous.session?.id
        val localRetry = previous.localRetry.takeIf { sameSession && selected?.frozenPayloadJson != null }
        val stage = when {
            selected == null -> "CATALOG"
            localRetry != null -> "RETRY_WRONG"
            selected.phase != "ANSWERING" -> "SUMMARY"
            saved.get<String>("view") in setOf("LEARN", "QUESTIONS", "READY") -> saved.get<String>("view")!!
            complete -> "READY"
            answers.isEmpty() && selected.position == 0 -> "LEARN"
            else -> "QUESTIONS"
        }
        val currentQuestion = pack?.questions?.getOrNull(selected?.position ?: 0)
        mutable.value = previous.copy(courses = snapshot.courses, session = selected, pack = pack, stage = stage,
            questionIndex = selected?.position ?: 0, answers = answers, correctCount = confirmed?.correctCount ?: grade?.correctCount,
            questionCount = confirmed?.questionCount ?: pack?.questions?.size ?: 0, passed = confirmed?.passed ?: grade?.passed,
            confirmedResult = confirmed, officialXp = snapshot.courses.sumOf { it.officialXp },
            syncError = selected?.let { repository.error(account, it.id) },
            feedback = previous.feedback.takeIf { sameSession && it?.questionId == currentQuestion?.id },
            fallbackQuestions = saved.get<ArrayList<String>>("fallbackQuestions")?.toSet().orEmpty(),
            wrongQuestionIds = grade?.marks?.filterNot { it.correct }?.map { it.questionId.toString() }.orEmpty(),
            localRetry = localRetry)
    }

    private suspend fun perform(afterRefresh: () -> Unit = {}, action: suspend () -> Unit) = actionMutex.withLock {
        mutable.value = mutable.value.copy(busy = true, error = null)
        try { action(); refreshLocal(); afterRefresh() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            refreshLocal()
            mutable.value = mutable.value.copy(error = when (error) {
                is IllegalStateException -> error.message ?: "Chưa thể mở bài học."
                else -> error.toUserMessage(gson)
            })
        } finally { mutable.value = mutable.value.copy(busy = false) }
    }
    suspend fun load() = perform { refreshLocal(); repository.loadCatalog(account) }
    suspend fun download(course: String) = perform { repository.download(account, course) }
    suspend fun openLesson(course: String, lesson: String) = perform {
        val selected = repository.open(account, course, lesson)
        if (saved.get<String>("session") != selected.id) {
            saved.remove<String>("view"); saved.remove<ArrayList<String>>("fallbackQuestions")
            mutable.value = mutable.value.copy(localRetry = null, feedback = null)
        }
        saved["session"] = selected.id
    }
    fun showQuestions() {
        if (state.value.stage == "LEARN") {
            saved["view"] = "QUESTIONS"; mutable.value = mutable.value.copy(stage = "QUESTIONS")
        }
    }
    suspend fun choose(question: String, answer: String) {
        val current = state.value; val session = current.session ?: return
        if (current.frozen) return
        val target = current.pack?.questions?.firstOrNull { it.id == question } ?: return
        perform(afterRefresh = {
            if (state.value.session?.id == session.id && state.value.pack?.questions?.getOrNull(state.value.questionIndex)?.id == question) {
                if (target.type == "FILL_GAP") mutable.value = mutable.value.copy(feedback = null)
                else showFeedback(target, answer)
            }
        }) {
            if (state.value.session?.id == session.id && !state.value.frozen)
                repository.answer(account, session.id, question, answer, current.questionIndex)
        }
    }
    fun checkAnswer() {
        val current = state.value
        if (current.frozen || current.busy) return
        val question = current.pack?.questions?.getOrNull(current.questionIndex) ?: return
        val answer = current.answers[question.id] ?: return
        if (hasAnswer(question, answer)) showFeedback(question, answer)
    }
    private fun showFeedback(question: PackedQuestion, answer: String) {
        val mark = PackGrader.mark(question, SubmittedAnswer(UUID.fromString(question.id), answer))
        mutable.value = mutable.value.copy(feedback = LessonQuestionFeedback(question.id, mark.correct, question.explanation))
    }
    fun useFallback(question: String) {
        if (state.value.pack?.questions?.any { it.id == question && it.type == "LISTEN_CHOICE" } != true) return
        val values = state.value.fallbackQuestions + question
        saved["fallbackQuestions"] = ArrayList(values)
        mutable.value = mutable.value.copy(fallbackQuestions = values)
    }
    suspend fun next() {
        val current = state.value; val session = current.session ?: return; val pack = current.pack ?: return
        if (current.frozen) return
        perform {
            val latest = state.value
            if (latest.session?.id == session.id && hasAnswer(pack.questions[current.questionIndex], latest.answers[pack.questions[current.questionIndex].id])) {
                if (current.questionIndex < pack.questions.lastIndex) repository.move(account, session.id, current.questionIndex + 1)
                else saved["view"] = "READY"
                mutable.value = mutable.value.copy(feedback = null)
            }
        }
    }
    suspend fun previous() {
        val current = state.value; val session = current.session ?: return
        if (current.frozen || current.questionIndex <= 0) return
        perform { repository.move(account, session.id, current.questionIndex - 1); saved["view"] = "QUESTIONS" }
    }
    suspend fun submit() {
        val session = state.value.session ?: return
        if (state.value.frozen) return
        perform {
            val current = state.value
            if (current.session?.id != session.id || current.frozen) return@perform
            check(current.pack?.questions?.all { hasAnswer(it, current.answers[it.id]) } == true) {
                "Hãy trả lời đủ câu hỏi; ô điền từ không được để trống."
            }
            repository.submit(account, session.id, isOffline()); saved.remove<String>("view")
        }
    }
    suspend fun retry() { if (state.value.session?.phase != "BLOCKED") perform { repository.retry(account) } }
    fun backToCourses() {
        saved.remove<String>("session"); saved.remove<String>("view"); saved.remove<ArrayList<String>>("fallbackQuestions")
        mutable.value = mutable.value.copy(session = null, pack = null, stage = "CATALOG", answers = emptyMap(),
            confirmedResult = null, error = null, feedback = null, localRetry = null, fallbackQuestions = emptySet())
    }
    suspend fun restart() {
        val current = state.value; val session = current.session ?: return; val pack = current.pack ?: return
        if (session.phase != "CONFIRMED") return
        perform {
            val next = repository.open(account, pack.courseId, session.lessonId, newAttempt = true)
            saved["session"] = next.id; saved["view"] = "LEARN"; saved.remove<ArrayList<String>>("fallbackQuestions")
            mutable.value = mutable.value.copy(localRetry = null, feedback = null)
        }
    }

    // Local retry methods intentionally never call a repository mutation or create another attempt.
    fun startWrongPractice() {
        val current = state.value
        if (!current.canRetryWrong || current.busy) return
        mutable.value = current.copy(stage = "RETRY_WRONG", localRetry = LocalWrongPractice(current.wrongQuestionIds), feedback = null)
    }
    fun chooseRetry(question: String, answer: String) {
        val current = state.value; val retry = current.localRetry ?: return
        if (!current.frozen || retry.questionIds.getOrNull(retry.index) != question) return
        val q = current.pack?.questions?.firstOrNull { it.id == question } ?: return
        if (q.type == "FILL_GAP" && answer.length > 500) {
            mutable.value = current.copy(error = "Ô điền từ tối đa 500 ký tự.")
            return
        }
        if (q.type != "FILL_GAP" && q.options.none { it.key == answer }) return
        val feedback = if (q.type != "FILL_GAP") retryFeedback(q, answer) else null
        mutable.value = current.copy(localRetry = retry.copy(answers = retry.answers + (question to answer), feedback = feedback))
    }
    fun checkRetry() {
        val current = state.value; val retry = current.localRetry ?: return
        val q = current.pack?.questions?.firstOrNull { it.id == retry.questionIds.getOrNull(retry.index) } ?: return
        val answer = retry.answers[q.id] ?: return
        if (hasAnswer(q, answer)) mutable.value = current.copy(localRetry = retry.copy(feedback = retryFeedback(q, answer)))
    }
    private fun retryFeedback(question: PackedQuestion, answer: String): LessonQuestionFeedback {
        val mark = PackGrader.mark(question, SubmittedAnswer(UUID.fromString(question.id), answer))
        return LessonQuestionFeedback(question.id, mark.correct, question.explanation)
    }
    fun nextRetry() {
        val current = state.value; val retry = current.localRetry ?: return
        if (retry.feedback == null) return
        if (retry.index < retry.questionIds.lastIndex)
            mutable.value = current.copy(localRetry = retry.copy(index = retry.index + 1, feedback = null))
        else finishWrongPractice()
    }
    fun finishWrongPractice() { mutable.value = mutable.value.copy(stage = "SUMMARY", localRetry = null) }
}
