package com.quocdat.lingolens.lesson

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quocdat.lingolens.assessment.core.LessonPack
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*
import java.io.IOException

interface StudyApi {
    @GET("api/v1/curriculum") suspend fun catalog(@Header("X-LingoLens-Account") account:Long):Response<ApiEnvelope<CatalogDto>>
    @POST("api/v1/curriculum/courses/{id}/enroll") suspend fun enroll(@Header("X-LingoLens-Account") account:Long,@Path("id") course:String):Response<ApiEnvelope<Unit>>
}
interface StudyGateway {
    suspend fun catalog(account:Long):CatalogDto
    suspend fun enroll(account:Long,course:String)
    suspend fun snapshot(account:Long,course:String):LessonCourseSnapshot
}
class RemoteStudyGateway(private val api:StudyApi,private val sync:LessonSyncGateway):StudyGateway {
    override suspend fun catalog(account:Long):CatalogDto {
        val response=api.catalog(account)
        if(!response.isSuccessful) throw retrofit2.HttpException(response)
        return response.body()?.takeIf{it.success}?.data ?: throw IOException("Invalid curriculum response")
    }
    override suspend fun enroll(account:Long,course:String){
        val response=api.enroll(account,course)
        if(!response.isSuccessful) throw retrofit2.HttpException(response)
        check(response.body()?.success==true)
    }
    override suspend fun snapshot(account:Long,course:String)=sync.snapshot(account,course)
}
data class StudyLesson(val pack:LessonPack,val canOpen:Boolean,val session:LessonSession?,val confirmedPassed:Boolean)
data class StudyCourse(val id:String,val title:String,val demo:Boolean,val ready:Boolean,val downloaded:Int,
    val lessons:List<StudyLesson>,val officialXp:Int)
data class StudySnapshot(val courses:List<StudyCourse>,val sessions:List<LessonSession>)

/** UI projection only. Durable content, answers, queue and confirmed progress remain in Room. */
class LessonStudyRepository(private val db:LearningDatabase,private val packs:ContentPackRepository,
    private val sessions:LessonSessionRepository,private val gateway:StudyGateway,
    private val isSignedIn:suspend(Long)->Boolean,private val scheduleSync:(Long)->Unit,private val gson:Gson=Gson()) {
    private val catalogs=java.util.concurrent.ConcurrentHashMap<Long,CatalogDto>()
    suspend fun signedIn(account:Long)=account>0 && isSignedIn(account)
    suspend fun loadCatalog(account:Long){
        check(signedIn(account));val catalog=gateway.catalog(account);check(signedIn(account));catalogs[account]=catalog
    }
    suspend fun download(account:Long,course:String){
        check(signedIn(account))
        if(db.lessonDao().curriculum(account,course)?.enrollmentStatus!="ACTIVE"){
            gateway.enroll(account,course);check(signedIn(account))
            val state=gateway.snapshot(account,course);check(signedIn(account))
            require(state.courseId==course && state.enrollmentStatus=="ACTIVE" && state.xpTotal>=0)
            db.withTransaction {
                check(signedIn(account))
                db.lessonDao().saveCurriculum(CurriculumAccountState(account,course,state.enrollmentStatus,gson.toJson(state.progress),state.xpTotal,System.currentTimeMillis()))
            }
        }
        packs.downloadCourse(account,course)
    }
    fun observe(account:Long):Flow<StudySnapshot> {
        val dao=db.lessonDao()
        return combine(dao.observePacks(account),dao.observeSessions(account),dao.observeOutbox(account),dao.observeCurriculum(account)){_,_,_,_->Unit}
            .map{read(account)}
    }
    suspend fun read(account:Long):StudySnapshot {
        if(!signedIn(account)) return StudySnapshot(emptyList(),emptyList())
        return db.withTransaction {
            if(!signedIn(account)) return@withTransaction StudySnapshot(emptyList(),emptyList())
            val dao=db.lessonDao();val all=dao.sessions(account).filter{it.mode=="LESSON"}
            val catalog=catalogs[account]?.courses.orEmpty()
            val ids=(catalog.map{it.id}+dao.courseIds(account)).distinct()
            val courses=ids.map{course->
                val ready=packs.ready(account,course);val saved=dao.completePacks(account,course)
                val official=dao.curriculum(account,course)
                val progress:List<ConfirmedLessonProgress> = official?.let{gson.fromJson(it.confirmedProgressJson,object:TypeToken<List<ConfirmedLessonProgress>>(){}.type)} ?: emptyList()
                val remote=catalog.firstOrNull{it.id==course}
                StudyCourse(course,remote?.title ?: "Chặng đã tải",remote?.contentStatus=="DEMO" || ready.any{it.contentStatus=="DEMO"},ready.size==3,
                    saved.map{it.lessonId}.distinct().size.coerceAtMost(3),ready.map{p->StudyLesson(p,sessions.canOpen(account,p),all.firstOrNull{it.lessonId==p.lessonId},progress.any{it.lessonId==p.lessonId && it.passed})},official?.confirmedXpTotal ?: 0)
            }
            if(!signedIn(account)) StudySnapshot(emptyList(),emptyList()) else StudySnapshot(courses,all)
        }
    }
    suspend fun open(account:Long,course:String,lesson:String,newAttempt:Boolean=false):LessonSession {
        check(signedIn(account));val available=packs.ready(account,course)
        check(available.size==3){"Chặng chưa tải đủ. Hãy kết nối mạng và tải lại."}
        val target=available.first{it.lessonId==lesson}
        val old=db.lessonDao().sessions(account).firstOrNull{it.lessonId==lesson && it.mode=="LESSON"}
        if(old!=null && !newAttempt) return sessions.resume(account,old.id)
        check(old==null || old.phase=="CONFIRMED"){"Phiên chưa được xác nhận; đáp án cũ vẫn được giữ."}
        return sessions.start(account,target.revisionId)
    }
    suspend fun pack(account:Long,session:LessonSession):LessonPack {
        check(signedIn(account));return packs.parse(checkNotNull(db.lessonDao().pack(account,session.revisionId)).contentJson)
    }
    suspend fun answer(account:Long,id:String,question:String,option:String,position:Int)=sessions.answer(account,id,question,option,position)
    suspend fun move(account:Long,id:String,position:Int)=sessions.move(account,id,position)
    suspend fun submit(account:Long,id:String,offline:Boolean)=sessions.submit(account,id,offline)
    suspend fun error(account:Long,id:String):String? {check(signedIn(account));return db.lessonDao().outbox(account,id)?.lastError}
    suspend fun retry(account:Long){check(signedIn(account));scheduleSync(account)}
}
