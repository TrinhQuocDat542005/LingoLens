package com.quocdat.lingolens.lesson

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.LessonSession
import com.quocdat.lingolens.data.remote.toUserMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class OfflineLessonUiState(val courses:List<StudyCourse> = emptyList(),val session:LessonSession?=null,
    val pack:LessonPack?=null,val stage:String="CATALOG",val questionIndex:Int=0,val answers:Map<String,String> = emptyMap(),
    val correctCount:Int?=null,val questionCount:Int=0,val passed:Boolean?=null,val confirmedResult:LessonSyncResult?=null,
    val officialXp:Int=0,val syncError:String?=null,val busy:Boolean=false,val error:String?=null) {
    val frozen get()=session?.frozenPayloadJson!=null
}

class OfflineLessonScreenModel(private val saved:SavedStateHandle,private val repository:LessonStudyRepository,
    private val account:Long,private val isOffline:()->Boolean,scope:CoroutineScope?=null):ViewModel() {
    companion object {
        fun factory(repository:LessonStudyRepository,account:Long,isOffline:()->Boolean):ViewModelProvider.Factory=viewModelFactory {
            initializer{OfflineLessonScreenModel(createSavedStateHandle(),repository,account,isOffline)}
        }
    }
    private val gson=Gson()
    private val mutable=MutableStateFlow(OfflineLessonUiState())
    val state:StateFlow<OfflineLessonUiState> = mutable
    private val refreshMutex=Mutex()
    init {
        // Only navigation is saved here. Never import the week 5 payload into Room.
        if(saved.get<Long>("durableAccount")!=account) saved.keys().toList().forEach{saved.remove<Any>(it)}
        saved["durableAccount"]=account
        (scope ?: viewModelScope).launch {
            try{repository.observe(account).collect{refreshLocal()}}
            catch(error:CancellationException){throw error}
            catch(error:Exception){mutable.value=mutable.value.copy(error=error.toUserMessage(gson))}
        }
    }
    suspend fun refreshLocal()=refreshMutex.withLock {
        val snapshot=repository.read(account)
        if(!repository.signedIn(account)){
            saved.remove<String>("session");saved.remove<String>("view")
            mutable.value=OfflineLessonUiState(error="Phiên đăng nhập đã thay đổi.");return@withLock
        }
        val selected=snapshot.sessions.firstOrNull{it.id==saved.get<String>("session")}
        val pack=selected?.let{repository.pack(account,it)}
        val answers:Map<String,String> = selected?.let{gson.fromJson(it.answersJson,object:TypeToken<Map<String,String>>(){}.type)} ?: emptyMap()
        val confirmed=selected?.takeIf{it.phase=="CONFIRMED"}?.serverResultJson?.let{gson.fromJson(it,LessonSyncResult::class.java)}
        val grade=if(selected!=null && pack!=null && answers.size==pack.questions.size) PackGrader.grade(pack.questions,answers.map{SubmittedAnswer(UUID.fromString(it.key),it.value)}) else null
        val stage=when {
            selected==null -> "CATALOG"
            selected.phase!="ANSWERING" -> "SUMMARY"
            saved.get<String>("view") in setOf("LEARN","QUESTIONS","READY") -> saved.get<String>("view")!!
            answers.size==pack!!.questions.size -> "READY"
            answers.isEmpty() && selected.position==0 -> "LEARN"
            else -> "QUESTIONS"
        }
        mutable.value=mutable.value.copy(courses=snapshot.courses,session=selected,pack=pack,stage=stage,
            questionIndex=selected?.position ?: 0,answers=answers,correctCount=confirmed?.correctCount ?: grade?.correctCount,
            questionCount=confirmed?.questionCount ?: pack?.questions?.size ?: 0,passed=confirmed?.passed ?: grade?.passed,
            confirmedResult=confirmed,officialXp=snapshot.courses.sumOf{it.officialXp},syncError=selected?.let{repository.error(account,it.id)})
    }
    private suspend fun perform(action:suspend()->Unit){
        if(mutable.value.busy) return
        mutable.value=mutable.value.copy(busy=true,error=null)
        try{action();refreshLocal()}
        catch(error:CancellationException){throw error}
        catch(error:Exception){refreshLocal();mutable.value=mutable.value.copy(error=when(error){
            is IllegalStateException -> error.message ?: "Chưa thể mở bài học."
            else -> error.toUserMessage(gson)
        })}
        finally{mutable.value=mutable.value.copy(busy=false)}
    }
    suspend fun load()=perform{refreshLocal();repository.loadCatalog(account)}
    suspend fun download(course:String)=perform{repository.download(account,course)}
    suspend fun openLesson(course:String,lesson:String)=perform{
        val selected=repository.open(account,course,lesson)
        if(saved.get<String>("session")!=selected.id) saved.remove<String>("view")
        saved["session"]=selected.id
    }
    fun showQuestions(){if(state.value.stage=="LEARN") {saved["view"]="QUESTIONS";mutable.value=mutable.value.copy(stage="QUESTIONS")}}
    suspend fun choose(question:String,option:String){
        val current=state.value;val session=current.session ?: return
        if(current.frozen) return
        perform{repository.answer(account,session.id,question,option,current.questionIndex)}
    }
    suspend fun next(){
        val current=state.value;val session=current.session ?: return;val pack=current.pack ?: return
        if(current.frozen || current.answers[pack.questions[current.questionIndex].id]==null) return
        perform{
            if(current.questionIndex<pack.questions.lastIndex) repository.move(account,session.id,current.questionIndex+1)
            else saved["view"]="READY"
        }
    }
    suspend fun previous(){
        val current=state.value;val session=current.session ?: return
        if(current.frozen || current.questionIndex<=0) return
        perform{repository.move(account,session.id,current.questionIndex-1);saved["view"]="QUESTIONS"}
    }
    suspend fun submit(){
        val current=state.value;val session=current.session ?: return
        if(current.frozen) return
        if(current.answers.size!=current.questionCount){mutable.value=current.copy(error="Hãy trả lời đủ câu hỏi trước khi nộp.");return}
        perform{repository.submit(account,session.id,isOffline());saved.remove<String>("view")}
    }
    suspend fun retry() {if(state.value.session?.phase!="BLOCKED") perform{repository.retry(account)}}
    fun backToCourses(){saved.remove<String>("session");saved.remove<String>("view");mutable.value=mutable.value.copy(session=null,pack=null,stage="CATALOG",answers=emptyMap(),confirmedResult=null,error=null)}
    suspend fun restart(){
        val current=state.value;val s=current.session ?: return;val pack=current.pack ?: return
        if(s.phase!="CONFIRMED") return
        perform{val next=repository.open(account,pack.courseId,s.lessonId,newAttempt=true);saved["session"]=next.id;saved["view"]="LEARN"}
    }
}
