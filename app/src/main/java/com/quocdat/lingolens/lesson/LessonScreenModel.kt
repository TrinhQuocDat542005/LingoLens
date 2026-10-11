package com.quocdat.lingolens.lesson

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class LessonUiState(val catalog: CatalogDto?=null,val lesson: LessonDto?=null,val stage: String="CATALOG",
    val questionIndex: Int=0,val answers: Map<String,String> = emptyMap(),val attemptId: String?=null,
    val result: AttemptResultDto?=null,val frozen: Boolean=false,val busy: Boolean=false,val error: String?=null)

class LessonScreenModel(private val saved: SavedStateHandle,private val gateway: LessonGateway,account: Long) : ViewModel() {
    companion object {
        fun factory(gateway: LessonGateway,account: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { LessonScreenModel(createSavedStateHandle(),gateway,account) }
        }
    }
    private val gson=Gson()
    private val mutable=MutableStateFlow(LessonUiState())
    val state: StateFlow<LessonUiState> = mutable
    init {
        if (saved.get<Long>("account")!=account) saved.keys().toList().forEach { saved.remove<Any>(it) }
        saved["account"]=account
        if (saved.get<String>("stage")=="SUBMITTING") saved["stage"]="READY"
        refresh()
    }
    private fun <T> read(key: String, type: Class<T>): T? = saved.get<String>(key)?.let { gson.fromJson(it,type) }
    private fun refresh(busy: Boolean=false,error: String?=null) {
        val selected=read("answers",Array<AnswerDto>::class.java)?.associate { it.questionId to it.optionKey }.orEmpty()
        mutable.value=LessonUiState(read("catalog",CatalogDto::class.java),read("lesson",LessonDto::class.java),saved["stage"]?:"CATALOG",
            saved["index"]?:0,selected,saved["uuid"],read("result",AttemptResultDto::class.java),saved.get<String>("payload")!=null,busy,error)
    }
    suspend fun loadCatalog() {
        if (state.value.busy) return
        refresh(busy=true)
        try { saved["catalog"]=gson.toJson(gateway.catalog()); refresh() }
        catch (error: CancellationException) { refresh(); throw error }
        catch (error: Exception) { refresh(error="Chưa tải được lộ trình. Kiểm tra kết nối và thử lại.") }
    }
    suspend fun open(course: CourseDto) {
        if (state.value.busy) return
        refresh(busy=true)
        try {
            gateway.enroll(course.id); val lesson=gateway.lesson(course.firstLessonId)
            saved["lesson"]=gson.toJson(lesson); newAttempt(); refresh()
        } catch (error: CancellationException) { refresh(); throw error }
        catch (error: Exception) { refresh(error=error.message ?: "Chưa mở được bài học.") }
    }
    fun showQuestions() { if (state.value.stage=="LEARN") { saved["stage"]="QUESTIONS"; refresh() } }
    fun choose(question: String, option: String) {
        val current=state.value
        if (current.frozen || current.busy || current.lesson?.questions?.any { it.id==question && it.options.any { choice -> choice.key==option } }!=true) return
        saved["answers"]=gson.toJson((current.answers+(question to option)).map { AnswerDto(it.key,it.value) }); refresh()
    }
    fun next() {
        val current=state.value; val questions=current.lesson?.questions ?: return
        if (current.answers[questions[current.questionIndex].id]==null) return
        if (current.questionIndex<questions.lastIndex) saved["index"]=current.questionIndex+1 else saved["stage"]="READY"
        refresh()
    }
    fun previous() { if (!state.value.frozen && state.value.questionIndex>0) { saved["index"]=state.value.questionIndex-1; refresh() } }
    fun restart() { if (state.value.stage=="SUMMARY") { newAttempt(); refresh() } }
    private fun newAttempt() {
        saved.remove<String>("payload"); saved.remove<String>("result")
        saved["uuid"]=UUID.randomUUID().toString(); saved["answers"]="[]"; saved["index"]=0; saved["stage"]="LEARN"
    }
    suspend fun submit() {
        val current=state.value; val lesson=current.lesson ?: return
        if (current.busy || current.stage=="SUMMARY") return
        if (current.answers.size!=lesson.questions.size) { refresh(error="Hãy trả lời đủ câu hỏi trước khi nộp."); return }
        // Save the entire immutable payload BEFORE dispatching any network request.
        val request=read("payload",AttemptRequestDto::class.java) ?: AttemptRequestDto(requireNotNull(current.attemptId),lesson.revisionId,"LESSON",System.currentTimeMillis(),false,
            lesson.questions.map { AnswerDto(it.id,current.answers.getValue(it.id)) }).also { saved["payload"]=gson.toJson(it) }
        saved["stage"]="SUBMITTING"; refresh(busy=true)
        try {
            val result=gateway.submit(request)
            saved["result"]=gson.toJson(result); saved["stage"]="SUMMARY"; refresh()
        } catch (error: CancellationException) { saved["stage"]="READY"; refresh(); throw error }
        catch (error: Exception) { saved["stage"]="READY"; refresh(error="Chưa xác nhận được kết quả. Kiểm tra kết nối rồi gửi lại."); return }
        // Catalog failure cannot turn an already confirmed attempt into a pending attempt.
        try { saved["catalog"]=gson.toJson(gateway.catalog()); refresh() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { refresh(error="Kết quả đã xác nhận; tổng XP sẽ cập nhật khi có kết nối.") }
    }
}
