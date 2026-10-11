package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.databind.ObjectMapper
import com.quocdat.lingolens.assessment.LessonRequestException
import com.quocdat.lingolens.assessment.core.*
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID

@Service
class ContentPackService(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper) {
    @Transactional(readOnly=true)
    fun manifest(course: UUID): PackManifest {
        val rows=jdbc.queryForList("SELECT r.id,l.id lesson_id,l.title,l.position,r.content_status FROM lesson_revisions r JOIN lessons l ON l.id=r.lesson_id JOIN course_units u ON u.id=l.unit_id JOIN courses c ON c.id=u.course_id WHERE c.id=? AND c.status='PUBLISHED' AND r.status='PUBLISHED' ORDER BY u.position,l.position",course)
        if(rows.size!=3) unavailable()
        return PackManifest(1,course.toString(),rows.map { row ->
            val revision=row["id"] as UUID; val bytes=pack(revision)
            PackReference(row["lesson_id"].toString(),revision.toString(),row["title"].toString(),(row["position"] as Number).toInt(),row["content_status"].toString(),
                "/api/v1/curriculum/revisions/$revision/pack",bytes.size.toLong(),hash(bytes))
        })
    }
    @Transactional(readOnly=true)
    fun pack(revision: UUID): ByteArray {
        val row=jdbc.queryForList("SELECT r.introduction,r.sentence_patterns::text,r.content_status,l.id lesson_id,l.title,l.position,u.course_id FROM lesson_revisions r JOIN lessons l ON l.id=r.lesson_id JOIN course_units u ON u.id=l.unit_id JOIN courses c ON c.id=u.course_id WHERE r.id=? AND r.status IN ('PUBLISHED','ARCHIVED') AND c.status IN ('PUBLISHED','ARCHIVED')",revision).firstOrNull() ?: unavailable()
        val questions=jdbc.queryForList("SELECT id,type,prompt,explanation,position,weight,answer_key::text FROM questions WHERE lesson_revision_id=? ORDER BY position",revision).map { q ->
            val id=q["id"] as UUID
            PackedQuestion(id.toString(),q["type"].toString(),q["prompt"].toString(),q["explanation"].toString(),(q["position"] as Number).toInt(),(q["weight"] as Number).toInt(),
                jdbc.queryForList("SELECT option_key,label,position FROM question_options WHERE question_id=? ORDER BY position",id).map { PackedOption(it["option_key"].toString(),it["label"].toString(),(it["position"] as Number).toInt()) },
                PackedAnswerKey(mapper.readTree(q["answer_key"].toString())["optionKey"].asText()))
        }
        if(questions.size !in 8..10) unavailable()
        val body=LessonPack(1,row["course_id"].toString(),row["lesson_id"].toString(),revision.toString(),row["title"].toString(),row["introduction"].toString(),row["content_status"].toString(),"en-US",(row["position"] as Number).toInt(),
            mapper.readTree(row["sentence_patterns"].toString()).map { mapper.treeToValue(it,PackedPattern::class.java) },
            jdbc.queryForList("SELECT content_snapshot::text FROM lesson_vocabularies WHERE lesson_revision_id=? ORDER BY position",String::class.java,revision).map { mapper.readValue(it,PackedWord::class.java) },questions)
        return mapper.writeValueAsBytes(body)
    }
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun unavailable(): Nothing = throw LessonRequestException(HttpStatus.NOT_FOUND,"LESSON_REVISION_UNAVAILABLE","Published content unavailable")
}
