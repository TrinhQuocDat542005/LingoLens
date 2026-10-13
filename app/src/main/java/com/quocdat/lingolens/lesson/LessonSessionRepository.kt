package com.quocdat.lingolens.lesson

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.quocdat.lingolens.assessment.core.*
import com.quocdat.lingolens.data.local.*
import java.security.MessageDigest
import java.util.UUID

class LessonSessionRepository(private val db:LearningDatabase,private val packs:ContentPackRepository,
    private val isSignedIn:suspend(Long)->Boolean,private val gson:Gson=Gson(),private val clock:()->Long=System::currentTimeMillis,
    private val scheduleSync:(Long)->Unit={},private val afterEnqueue:()->Unit={}) {
    suspend fun start(account:Long,revision:String,mode:String="LESSON"):LessonSession=db.withTransaction {
        signedIn(account);require(mode in setOf("LESSON","PRACTICE"))
        val pack=pack(account,revision)
        check(db.lessonDao().curriculum(account,pack.courseId)?.enrollmentStatus=="ACTIVE"){"Enroll online before starting lessons"}
        if(mode=="LESSON") check(canOpen(account,pack)){"Complete previous lesson first"}
        val now=clock();val session=LessonSession(account,UUID.randomUUID().toString(),pack.lessonId,revision,mode,
            gson.toJson(pack.questions.map{it.id}),0,"{}","ANSWERING",now,now)
        db.lessonDao().saveSession(session);signedIn(account);session
    }
    suspend fun resume(account:Long,id:String):LessonSession {
        signedIn(account)
        return checkNotNull(db.lessonDao().session(account,id)){"Session unavailable for this account"}
    }
    suspend fun answer(account:Long,id:String,question:String,option:String,position:Int)=db.withTransaction {
        val session=resume(account,id);check(session.phase=="ANSWERING" && session.frozenPayloadJson==null){"Submission is immutable"}
        val pack=pack(account,session.revisionId)
        require(position in pack.questions.indices)
        val target=requireNotNull(pack.questions.firstOrNull{it.id==question}){"Invalid question or option"}
        if(target.type=="FILL_GAP") require(option.length<=500){"Fill answer is too long"}
        else require(target.options.any{it.key==option}){"Invalid question or option"}
        val answers=answers(session).toMutableMap();answers[question]=option
        db.lessonDao().saveSession(session.copy(position=position,answersJson=gson.toJson(answers),updatedAt=clock()))
        signedIn(account)
    }
    suspend fun provisionalGrade(account:Long,id:String):LessonGrade {
        val session=resume(account,id)
        return PackGrader.grade(pack(account,session.revisionId).questions,answers(session).map{SubmittedAnswer(UUID.fromString(it.key),it.value)})
    }
    suspend fun move(account:Long,id:String,position:Int)=db.withTransaction {
        val session=resume(account,id)
        check(session.phase=="ANSWERING" && session.frozenPayloadJson==null){"Submission is immutable"}
        require(position in pack(account,session.revisionId).questions.indices)
        db.lessonDao().saveSession(session.copy(position=position,updatedAt=clock()));signedIn(account)
    }
    suspend fun submit(account:Long,id:String,offline:Boolean):String {
        val payload=db.withTransaction {
            val session=resume(account,id)
            if(session.frozenPayloadJson!=null) return@withTransaction session.frozenPayloadJson
            check(session.phase=="ANSWERING")
            val pack=pack(account,session.revisionId)
            val answers=answers(session)
            PackGrader.grade(pack.questions,answers.map{SubmittedAnswer(UUID.fromString(it.key),it.value)})
            val now=clock();require(now>0)
            val body=gson.toJson(AttemptRequestDto(session.id,session.revisionId,session.mode,now,offline,
                pack.questions.map{AnswerDto(it.id,answers.getValue(it.id))}))
            val hash=MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
            db.lessonDao().saveSession(session.copy(phase="QUEUED",updatedAt=now,frozenPayloadJson=body))
            db.lessonDao().enqueue(LessonAttemptOutbox(account,session.id,session.id,body,hash,"PENDING",now,0))
            afterEnqueue();signedIn(account);body
        }
        scheduleSync(account);return payload
    }
    private suspend fun pack(account:Long,revision:String):LessonPack = packs.parse(checkNotNull(db.lessonDao().pack(account,revision)){"Complete pack required"}.contentJson)
    suspend fun canOpen(account:Long,target:LessonPack):Boolean {
        signedIn(account)
        if(db.lessonDao().curriculum(account,target.courseId)?.enrollmentStatus!="ACTIVE") return false
        val course=packs.ready(account,target.courseId)
        if(course.size!=3) return false
        val confirmed=JsonParser.parseString(db.lessonDao().curriculum(account,target.courseId)!!.confirmedProgressJson).asJsonArray
            .filter{it.asJsonObject["passed"]?.asBoolean==true}.map{it.asJsonObject["lessonId"].asString}.toSet()
        val provisional=db.lessonDao().sessions(account).filter{it.mode=="LESSON" && it.phase in setOf("QUEUED","WAITING_PREREQUISITE")}.filter{session ->
            try{PackGrader.grade(pack(account,session.revisionId).questions,answers(session).map{SubmittedAnswer(UUID.fromString(it.key),it.value)}).passed}
            catch(error:kotlinx.coroutines.CancellationException){throw error}catch(_:Exception){false}
        }.map{it.lessonId}.toSet()
        return course.filter{it.position<target.position}.all{it.lessonId in confirmed || it.lessonId in provisional}
    }
    private fun answers(session:LessonSession):Map<String,String> = gson.fromJson(session.answersJson,object:TypeToken<Map<String,String>>(){}.type)
    private suspend fun signedIn(account:Long){check(account>0 && isSignedIn(account)){"Account changed or signed out"}}
}
