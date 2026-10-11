package com.quocdat.lingolens.assessment

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Service
class LessonService(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper) {
    @Transactional(readOnly=true)
    fun catalog(account: Long): CurriculumCatalog {
        val rows=jdbc.queryForList("SELECT c.id,c.title,c.content_status,l.id lesson_id,r.id revision_id FROM courses c JOIN course_units u ON u.course_id=c.id JOIN lessons l ON l.unit_id=u.id JOIN lesson_revisions r ON r.lesson_id=l.id AND r.status='PUBLISHED' WHERE c.status='PUBLISHED' ORDER BY c.created_at,u.position,l.position")
        val courses=rows.groupBy { it["id"] }.values.map { group ->
            val row=group.first(); val lesson=row["lesson_id"] as UUID
            CourseCard(row["id"] as UUID,row["title"].toString(),row["content_status"].toString(),lesson,row["revision_id"] as UUID,
                jdbc.queryForObject("SELECT count(*) FROM lesson_progress WHERE user_id=? AND lesson_id=? AND first_passed_at IS NOT NULL",Int::class.java,account,lesson)!!>0)
        }
        return CurriculumCatalog(courses,jdbc.queryForObject("SELECT COALESCE(sum(amount),0) FROM xp_events WHERE user_id=?",Int::class.java,account)!!)
    }
    @Transactional
    fun enroll(account: Long, course: UUID) {
        lockAccount(account)
        checkRequest(jdbc.queryForObject("SELECT count(*) FROM courses WHERE id=? AND status='PUBLISHED'",Int::class.java,course)!! == 1,"Course unavailable")
        val active=jdbc.queryForList("SELECT course_id FROM enrollments WHERE user_id=? AND status='ACTIVE'",UUID::class.java,account).firstOrNull()
        if (active==course) return
        if (active!=null) throw LessonRequestException(HttpStatus.CONFLICT,"ACTIVE_COURSE_EXISTS","Another course is active")
        jdbc.update("INSERT INTO enrollments(user_id,course_id,status,enrolled_at) VALUES (?,?,'ACTIVE',?) ON CONFLICT(user_id,course_id) DO UPDATE SET status='ACTIVE',completed_at=NULL",account,course,Timestamp.from(Instant.now()))
    }
    @Transactional(readOnly=true)
    fun lesson(account: Long, lesson: UUID): LessonView {
        val row=jdbc.queryForList("SELECT r.id revision_id,r.introduction,r.sentence_patterns::text,r.content_status,l.id lesson_id,l.title,l.position lesson_position,u.position unit_position,u.course_id FROM lesson_revisions r JOIN lessons l ON l.id=r.lesson_id JOIN course_units u ON u.id=l.unit_id JOIN courses c ON c.id=u.course_id WHERE l.id=? AND r.status='PUBLISHED' AND c.status='PUBLISHED'",lesson).firstOrNull()
            ?: throw LessonRequestException(HttpStatus.NOT_FOUND,"LESSON_NOT_FOUND","Lesson unavailable")
        requireAvailable(account,row)
        val revision=row["revision_id"] as UUID
        val questions=jdbc.queryForList("SELECT id,prompt,position FROM questions WHERE lesson_revision_id=? ORDER BY position",revision).map {
            val id=it["id"] as UUID
            QuestionView(id,it["prompt"].toString(),(it["position"] as Number).toInt(),jdbc.queryForList("SELECT option_key,label,position FROM question_options WHERE question_id=? ORDER BY position",id).map { option ->
                OptionView(option["option_key"].toString(),option["label"].toString(),(option["position"] as Number).toInt())
            })
        }
        return LessonView(lesson,revision,row["title"].toString(),row["introduction"].toString(),row["content_status"].toString(),mapper.readTree(row["sentence_patterns"].toString()),
            jdbc.queryForList("SELECT content_snapshot::text FROM lesson_vocabularies WHERE lesson_revision_id=? ORDER BY position",String::class.java,revision).map(mapper::readTree),questions)
    }
    @Transactional
    fun submit(account: Long, request: SubmitLessonRequest): AttemptResult {
        checkRequest(request.mode in setOf("LESSON","PRACTICE") && !request.submittedOffline,"Online LESSON/PRACTICE only in week 5")
        checkRequest(request.clientCompletedAt>0 && request.clientCompletedAt<=System.currentTimeMillis()+86_400_000,"Invalid client timestamp")
        lockAccount(account)
        // Cross-account UUID races are also serialized; no result ownership is leaked.
        jdbc.execute("SELECT pg_advisory_xact_lock(${java.nio.ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(request.id.toString().toByteArray())).long})")
        val hash=requestHash(request)
        val previous=jdbc.queryForList("SELECT user_id,request_hash FROM lesson_attempts WHERE id=?",request.id).firstOrNull()
        if (previous!=null) {
            if ((previous["user_id"] as Number).toLong()!=account) notFound()
            if (previous["request_hash"]!=hash) throw LessonRequestException(HttpStatus.CONFLICT,"ATTEMPT_PAYLOAD_CONFLICT","Attempt UUID already has a different payload")
            return result(account,request.id)
        }
        val row=jdbc.queryForList("SELECT l.id lesson_id,l.position lesson_position,u.position unit_position,u.course_id FROM lesson_revisions r JOIN lessons l ON l.id=r.lesson_id JOIN course_units u ON u.id=l.unit_id WHERE r.id=? AND r.status IN ('PUBLISHED','ARCHIVED')",request.lessonRevisionId).firstOrNull()
            ?: throw LessonRequestException(HttpStatus.NOT_FOUND,"LESSON_NOT_FOUND","Revision unavailable")
        if (request.mode=="LESSON") requireAvailable(account,row)
        val questions=jdbc.queryForList("SELECT id,weight,answer_key::text FROM questions WHERE lesson_revision_id=? ORDER BY position",request.lessonRevisionId).map {
            val id=it["id"] as UUID
            GradingQuestion(id,(it["weight"] as Number).toInt(),jdbc.queryForList("SELECT option_key FROM question_options WHERE question_id=?",String::class.java,id).toSet(),mapper.readTree(it["answer_key"].toString())["optionKey"].asText())
        }
        checkRequest(questions.size in 8..10,"Invalid lesson question count")
        val grade=LessonGrader.grade(questions,request.answers); val now=Timestamp.from(Instant.now())
        jdbc.update("INSERT INTO lesson_attempts VALUES (?,?,?,?,?,?,?,?,?,?)",request.id,account,request.lessonRevisionId,request.mode,grade.scorePercent,grade.passed,false,Timestamp(request.clientCompletedAt),now,hash)
        grade.marks.forEach { mark -> jdbc.update("INSERT INTO attempt_answers VALUES (?,?,?,CAST(? AS JSONB),?,?)",request.id,mark.questionId,request.lessonRevisionId,mapper.writeValueAsString(mapOf("optionKey" to mark.optionKey)),mark.correct,mark.awardedWeight) }
        if (request.mode=="LESSON") {
            val lesson=row["lesson_id"] as UUID
            val progress=jdbc.queryForList("SELECT best_score_percent,first_passed_at FROM lesson_progress WHERE user_id=? AND lesson_id=?",account,lesson).firstOrNull()
            val firstPass=grade.passed && progress?.get("first_passed_at")==null
            if (progress==null) jdbc.update("INSERT INTO lesson_progress VALUES (?,?,?,?,?,?)",account,lesson,grade.scorePercent,if (grade.passed) now else null,request.id,now)
            else jdbc.update("UPDATE lesson_progress SET best_score_percent=?,first_passed_at=COALESCE(first_passed_at,?),last_attempt_id=?,updated_at=? WHERE user_id=? AND lesson_id=?",
                maxOf((progress["best_score_percent"] as Number).toInt(),grade.scorePercent),if (grade.passed) now else null,request.id,now,account,lesson)
            if (firstPass) jdbc.update("INSERT INTO xp_events VALUES (?,?,'LESSON_FIRST_PASS',?,30,1,?)",request.id,account,lesson.toString(),now)
        }
        return result(account,request.id)
    }
    @Transactional(readOnly=true)
    fun result(account: Long, id: UUID): AttemptResult {
        val row=jdbc.queryForList("SELECT a.id,r.lesson_id,a.mode,a.score_percent,a.passed FROM lesson_attempts a JOIN lesson_revisions r ON r.id=a.lesson_revision_id WHERE a.id=? AND a.user_id=?",id,account).firstOrNull() ?: notFound()
        val correct=jdbc.queryForObject("SELECT count(*) FROM attempt_answers WHERE attempt_id=? AND is_correct",Int::class.java,id)!!
        val total=jdbc.queryForObject("SELECT count(*) FROM attempt_answers WHERE attempt_id=?",Int::class.java,id)!!
        val xp=jdbc.queryForObject("SELECT COALESCE(sum(amount),0) FROM xp_events WHERE id=? AND user_id=?",Int::class.java,id,account)!!
        return AttemptResult(id,row["lesson_id"] as UUID,row["mode"].toString(),correct,total,(row["score_percent"] as Number).toInt(),row["passed"] as Boolean,xp)
    }
    private fun requestHash(request: SubmitLessonRequest): String {
        val canonical=linkedMapOf("revision" to request.lessonRevisionId.toString(),"mode" to request.mode,"completed" to request.clientCompletedAt,"offline" to request.submittedOffline,
            "answers" to request.answers.sortedBy { it.questionId.toString() }.map { listOf(it.questionId.toString(),it.optionKey) })
        return MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical)).joinToString("") { "%02x".format(it) }
    }
    private fun lockAccount(account: Long) { jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR NO KEY UPDATE",Long::class.java,account) }
    private fun requireAvailable(account: Long, row: Map<String,Any?>) {
        checkRequest(jdbc.queryForObject("SELECT count(*) FROM enrollments WHERE user_id=? AND course_id=? AND status='ACTIVE'",Int::class.java,account,row["course_id"])!! == 1,"Enroll before starting lesson")
        val blocked=jdbc.queryForObject("SELECT count(*) FROM lessons l JOIN course_units u ON u.id=l.unit_id WHERE u.course_id=? AND (u.position<? OR (u.position=? AND l.position<?)) AND NOT EXISTS(SELECT 1 FROM lesson_progress p WHERE p.user_id=? AND p.lesson_id=l.id AND p.first_passed_at IS NOT NULL)",Int::class.java,row["course_id"],row["unit_position"],row["unit_position"],row["lesson_position"],account)!!
        checkRequest(blocked==0,"Complete previous lesson first")
    }
    private fun checkRequest(valid: Boolean, message: String) { if (!valid) throw LessonRequestException(HttpStatus.BAD_REQUEST,"LESSON_REQUEST_INVALID",message) }
    private fun notFound(): Nothing = throw LessonRequestException(HttpStatus.NOT_FOUND,"ATTEMPT_NOT_FOUND","Attempt not found")
}
