package com.quocdat.lingolens.lesson

import androidx.room.withTransaction
import com.google.gson.Gson
import com.quocdat.lingolens.data.local.*
import com.quocdat.lingolens.data.remote.dto.ApiEnvelope
import com.quocdat.lingolens.data.remote.dto.ApiErrorDto
import com.quocdat.lingolens.sync.SyncOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.*
import java.io.IOException
import java.security.MessageDigest

data class LessonSyncResult(val id:String,val lessonId:String,val mode:String,val correctCount:Int,val questionCount:Int,
    val scorePercent:Int,val passed:Boolean,val xpAwarded:Int,val confirmationStatus:String,val retryable:Boolean,val code:String?=null)
data class ConfirmedLessonProgress(val lessonId:String,val bestScorePercent:Int,val passed:Boolean)
data class LessonCourseSnapshot(val courseId:String,val enrollmentStatus:String,val progress:List<ConfirmedLessonProgress>,val xpTotal:Int)
interface LessonSyncGateway {
    suspend fun submit(account:Long,request:AttemptRequestDto):LessonSyncResult
    suspend fun snapshot(account:Long,course:String):LessonCourseSnapshot
}
interface LessonSyncApi {
    @POST("api/v1/lesson-attempts") suspend fun submit(@Header("X-LingoLens-Account") account:Long,@Body request:AttemptRequestDto):Response<ApiEnvelope<LessonSyncResult>>
    @GET("api/v1/curriculum/courses/{id}/state") suspend fun snapshot(@Header("X-LingoLens-Account") account:Long,@Path("id") course:String):Response<ApiEnvelope<LessonCourseSnapshot>>
}
class RemoteLessonSyncGateway(private val api:LessonSyncApi):LessonSyncGateway {
    override suspend fun submit(account:Long,request:AttemptRequestDto)=body(api.submit(account,request))
    override suspend fun snapshot(account:Long,course:String)=body(api.snapshot(account,course))
    private fun <T> body(response:Response<ApiEnvelope<T>>):T {
        if(!response.isSuccessful) throw HttpException(response)
        return response.body()?.takeIf{it.success}?.data ?: throw IOException("Invalid lesson sync response")
    }
}
class LessonSyncEngine(private val db:LearningDatabase,private val gateway:LessonSyncGateway,
    private val isSignedIn:suspend(Long)->Boolean,private val gson:Gson=Gson(),private val beforeAcknowledge:()->Unit={}) {
    private val mutex=Mutex()
    suspend fun run(account:Long):SyncOutcome=mutex.withLock {
        if(!isSignedIn(account)) return@withLock SyncOutcome.PAUSED
        var retry=false;var blocked=false
        val operations=db.lessonDao().pending(account)
        val ordered=operations.map{op->
            val session=db.lessonDao().session(account,op.sessionId)
            val pack=session?.let{db.lessonDao().pack(account,it.revisionId)}
            val position=pack?.let{gson.fromJson(it.contentJson,com.quocdat.lingolens.assessment.core.LessonPack::class.java).position} ?: Int.MAX_VALUE
            Triple(op,pack?.courseId ?: "",position)
        }.sortedWith(compareBy<Triple<LessonAttemptOutbox,String,Int>>{it.second}.thenBy{it.third}.thenBy{it.first.queuedAt}.thenBy{it.first.attemptId})
        for((operation,_,_) in ordered.take(100)) {
            if(!isSignedIn(account)) return@withLock SyncOutcome.PAUSED
            if(operation.state=="BLOCKED"){blocked=true;continue}
            var applyingResult=false
            try {
                val session=checkNotNull(db.lessonDao().session(account,operation.sessionId))
                val pack=checkNotNull(db.lessonDao().pack(account,session.revisionId))
                val hash=MessageDigest.getInstance("SHA-256").digest(operation.payloadJson.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
                check(operation.payloadHash==hash && session.frozenPayloadJson==operation.payloadJson){"Immutable attempt payload corrupted"}
                val request=gson.fromJson(operation.payloadJson,AttemptRequestDto::class.java)
                check(request.id==operation.attemptId && request.lessonRevisionId==session.revisionId)
                if(!isSignedIn(account)) return@withLock SyncOutcome.PAUSED
                val result=gateway.submit(account,request)
                if(!isSignedIn(account)) return@withLock SyncOutcome.PAUSED
                if(result.id!=operation.attemptId || result.lessonId!=session.lessonId || result.mode!=session.mode ||
                    result.scorePercent !in 0..100 || result.questionCount !in 8..10 || result.correctCount !in 0..result.questionCount ||
                    result.confirmationStatus !in setOf("CONFIRMED","WAITING_PREREQUISITE") ||
                    result.retryable!=(result.confirmationStatus=="WAITING_PREREQUISITE")) throw IOException("Unexpected lesson acknowledgement")
                val snapshot=if(!result.retryable) gateway.snapshot(account,pack.courseId) else null
                if(snapshot!=null) validateSnapshot(snapshot,pack.courseId)
                if(!isSignedIn(account)) return@withLock SyncOutcome.PAUSED
                applyingResult=true
                db.withTransaction {
                    check(isSignedIn(account))
                    db.lessonDao().saveSession(session.copy(phase=result.confirmationStatus,serverResultJson=gson.toJson(result),updatedAt=System.currentTimeMillis()))
                    if(snapshot!=null) saveSnapshot(account,snapshot)
                    beforeAcknowledge()
                    db.lessonDao().updateOutbox(operation.copy(state=result.confirmationStatus,lastError=result.code))
                    check(isSignedIn(account))
                }
                applyingResult=false
                if(result.retryable) retry=true
            } catch(error:CancellationException){throw error
            } catch(error:Exception){
                if(!isSignedIn(account)) return@withLock SyncOutcome.PAUSED
                if(error is HttpException && error.code() in listOf(401,403)) return@withLock SyncOutcome.PAUSED
                val transient=applyingResult || error is IOException || (error is HttpException && (error.code()>=500 || error.code() in listOf(408,429)))
                val code=if(error is HttpException) runCatching{gson.fromJson(error.response()?.errorBody()?.string(),ApiErrorDto::class.java)?.code}.getOrNull() else null
                db.withTransaction {
                    check(isSignedIn(account))
                    db.lessonDao().updateOutbox(operation.copy(state=if(transient) operation.state else "BLOCKED",retryCount=operation.retryCount+1,lastError=code ?: if(transient) "LESSON_SYNC_RETRY" else "LESSON_LOCAL_INVALID"))
                    if(!transient) db.lessonDao().session(account,operation.sessionId)?.let{db.lessonDao().saveSession(it.copy(phase="BLOCKED"))}
                }
                if(transient) retry=true else blocked=true
            }
        }
        if(!isSignedIn(account)) return@withLock SyncOutcome.PAUSED
        try {
            for(course in db.lessonDao().courseIds(account)){
                val hasUnconfirmed=db.lessonDao().pending(account).filter{it.state!="BLOCKED"}.any{operation ->
                    db.lessonDao().session(account,operation.sessionId)?.let{db.lessonDao().pack(account,it.revisionId)?.courseId}==course
                }
                if(hasUnconfirmed) continue
                val snapshot=gateway.snapshot(account,course);validateSnapshot(snapshot,course)
                if(!isSignedIn(account)) return@withLock SyncOutcome.PAUSED
                db.withTransaction{check(isSignedIn(account));saveSnapshot(account,snapshot)}
            }
        }catch(error:CancellationException){throw error
        }catch(error:Exception){
            if(!isSignedIn(account) || (error is HttpException && error.code() in listOf(401,403))) return@withLock SyncOutcome.PAUSED
            retry=true
        }
        if(ordered.size>100) retry=true
        when{retry->SyncOutcome.RETRY;blocked->SyncOutcome.BLOCKED;else->SyncOutcome.COMPLETE}
    }
    private fun validateSnapshot(snapshot:LessonCourseSnapshot,course:String){
        if(snapshot.courseId!=course || snapshot.xpTotal<0 || snapshot.enrollmentStatus !in setOf("NONE","ACTIVE","PAUSED","COMPLETED") ||
            snapshot.progress.any{it.bestScorePercent !in 0..100} || snapshot.progress.map{it.lessonId}.distinct().size!=snapshot.progress.size) throw IOException("Invalid course snapshot")
    }
    private suspend fun saveSnapshot(account:Long,snapshot:LessonCourseSnapshot){
        db.lessonDao().saveCurriculum(CurriculumAccountState(account,snapshot.courseId,snapshot.enrollmentStatus,gson.toJson(snapshot.progress),snapshot.xpTotal,System.currentTimeMillis()))
    }
}
