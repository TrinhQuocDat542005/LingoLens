package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Explicit dev/test import. Revision replacement is atomic, never edits published children. */
class ContentSeedImporter(private val jdbc:JdbcTemplate,private val mapper:ObjectMapper,private val transaction:TransactionTemplate) {
    fun import(bytes:ByteArray,profiles:Set<String>):Boolean {
        require(profiles.any{it in setOf("dev","test")} && profiles.none{it in setOf("prod","production")}){"Seed runner is dev/test only"}
        require(bytes.size<=2_000_000){"Seed too large"}
        val root=try{mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(bytes).also(SeedValidator::validate)}
            catch(error:Exception){throw IllegalArgumentException("Invalid seed: ${error.message}",error)}
        require(root["contentStatus"].asText()=="DEMO"){"REVIEWED content requires a reviewed main migration"}
        val pack=uuid(root,"packId");val checksum=hash(bytes);val course=root["course"];val topic=root["topic"];val unit=root["unit"]
        return transaction.execute {
            // Course lock serializes different revision packs too. Legacy pack-scoped locking did not.
            advisory(uuid(course));advisory(pack)
            val previous=jdbc.queryForList("SELECT checksum FROM content_import_receipts WHERE pack_id=?",String::class.java,pack).firstOrNull()
            if(previous!=null){require(previous==checksum){"Pack UUID already imported with a different checksum"};return@execute false}
            val now=java.sql.Timestamp.from(Instant.now())
            // Every reuse checks exact stable metadata. No ON CONFLICT UPDATE can mutate old content.
            reuse("topics",uuid(topic),mapOf("slug" to s(topic,"slug"),"title" to s(topic,"title"),"description" to s(topic,"description"),"icon_key" to s(topic,"iconKey"),"display_order" to topic["displayOrder"].asInt(),"is_published" to true)) {
                jdbc.update("INSERT INTO topics VALUES (?,?,?,?,?,?,TRUE,?,?)",uuid(topic),s(topic,"slug"),s(topic,"title"),s(topic,"description"),s(topic,"iconKey"),topic["displayOrder"].asInt(),now,now)
            }
            reuse("courses",uuid(course),mapOf("code" to s(course,"code"),"title" to s(course,"title"),"description" to s(course,"description"),"level" to s(course,"level"),"status" to "PUBLISHED","content_status" to "DEMO")) {
                jdbc.update("INSERT INTO courses(id,code,title,description,level,status,published_at,created_at,content_status,pack_id) VALUES (?,?,?,?,?,'PUBLISHED',?,?,'DEMO',?)",uuid(course),s(course,"code"),s(course,"title"),s(course,"description"),s(course,"level"),now,now,pack)
            }
            reuse("course_units",uuid(unit),mapOf("course_id" to uuid(course),"topic_id" to uuid(topic),"title" to s(unit,"title"),"position" to unit["position"].asInt())) {
                jdbc.update("INSERT INTO course_units VALUES (?,?,?,?,?)",uuid(unit),uuid(course),uuid(topic),s(unit,"title"),unit["position"].asInt())
            }
            val vocabulary=root["vocabularies"].associate { word ->
                val existing=jdbc.queryForList("SELECT id,translation,phonetic,part_of_speech,definition,example_sentence,level FROM vocabularies WHERE lower(word)=?",s(word,"word"))
                val id=if(existing.isNotEmpty()) {
                    require(existing.size==1);val row=existing.single()
                    val expected=mapOf("translation" to s(word,"translation"),"phonetic" to s(word,"phonetic"),"part_of_speech" to s(word,"partOfSpeech"),"definition" to s(word,"definition"),"example_sentence" to s(word,"example"),"level" to s(word,"level"))
                    require(expected.all{(key,value)->row[key]==value}){"Incompatible existing word: ${s(word,"word")}"};(row["id"] as Number).toLong()
                } else jdbc.queryForObject("INSERT INTO vocabularies(word,translation,phonetic,part_of_speech,definition,example_sentence,level) VALUES (?,?,?,?,?,?,?) RETURNING id",Long::class.java,s(word,"word"),s(word,"translation"),s(word,"phonetic"),s(word,"partOfSpeech"),s(word,"definition"),s(word,"example"),s(word,"level"))!!
                val position=root["vocabularies"].toList().indexOf(word)
                val membership=jdbc.queryForList("SELECT display_order FROM topic_vocabularies WHERE topic_id=? AND vocabulary_id=?",Int::class.java,uuid(topic),id).firstOrNull()
                if(membership==null)jdbc.update("INSERT INTO topic_vocabularies VALUES (?,?,?)",uuid(topic),id,position)
                else require(membership==position){"Incompatible topic vocabulary position: ${s(word,"word")}"}
                s(word,"word") to (id to word)
            }
            root["lessons"].forEach { lesson ->
                reuse("lessons",uuid(lesson),mapOf("unit_id" to uuid(unit),"title" to s(lesson,"title"),"position" to lesson["position"].asInt(),"estimated_minutes" to lesson["estimatedMinutes"].asInt())) {
                    jdbc.update("INSERT INTO lessons VALUES (?,?,?,?,?)",uuid(lesson),uuid(unit),s(lesson,"title"),lesson["position"].asInt(),lesson["estimatedMinutes"].asInt())
                }
                val revision=lesson["revision"];val revisionId=uuid(revision)
                val highest=jdbc.queryForObject("SELECT COALESCE(max(revision_no),0) FROM lesson_revisions WHERE lesson_id=?",Int::class.java,uuid(lesson))!!
                require(revision["revisionNo"].asInt()>highest){"Revision number must increase for lesson ${uuid(lesson)}: existing=$highest incoming=${revision["revisionNo"].asInt()}"}
                jdbc.update("INSERT INTO lesson_revisions(id,lesson_id,revision_no,introduction,sentence_patterns,status,published_at,created_at,content_status,pack_id) VALUES (?,?,?,?,CAST(? AS JSONB),'DRAFT',?,?,'DEMO',?)",revisionId,uuid(lesson),revision["revisionNo"].asInt(),s(revision,"introduction"),mapper.writeValueAsString(revision["sentencePatterns"]),now,now,pack)
                lesson["vocabularyWords"].forEachIndexed { index,word ->val(id,content)=vocabulary.getValue(word.asText());jdbc.update("INSERT INTO lesson_vocabularies VALUES (?,?,?,CAST(? AS JSONB))",revisionId,id,index+1,mapper.writeValueAsString(content))}
                lesson["questions"].forEach { q ->
                    // LISTEN fallback is persisted in answer_key JSONB; V10 already has speech_text.
                    jdbc.update("INSERT INTO questions(id,lesson_revision_id,type,prompt,speech_text,explanation,answer_key,position,weight) VALUES (?,?,?,?,?,?,CAST(? AS JSONB),?,?)",uuid(q),revisionId,s(q,"type"),s(q,"prompt"),q["speechText"]?.asText(),s(q,"explanation"),mapper.writeValueAsString(q["answerKey"]),q["position"].asInt(),q["weight"].asInt())
                    q["options"].forEach{option->jdbc.update("INSERT INTO question_options VALUES (?,?,?,?)",uuid(q),s(option,"key"),s(option,"label"),option["position"].asInt())}
                }
                // All child INSERTs finish while DRAFT. Archive only status of old revision, never its data.
                jdbc.update("UPDATE lesson_revisions SET status='ARCHIVED' WHERE lesson_id=? AND status='PUBLISHED'",uuid(lesson))
                jdbc.update("UPDATE lesson_revisions SET status='PUBLISHED' WHERE id=?",revisionId)
            }
            jdbc.update("INSERT INTO content_import_receipts VALUES (?,?,'DEMO',?)",pack,checksum,now)
            true
        }!!
    }
    private fun reuse(table:String,id:UUID,expected:Map<String,Any>,create:()->Unit) {
        val old=jdbc.queryForList("SELECT * FROM $table WHERE id=?",id).firstOrNull()
        if(old==null){create();return}
        require(expected.all{(key,value)-> val actual=old[key];if(actual is Number && value is Number)actual.toLong()==value.toLong() else actual==value}){"Incompatible existing $table metadata: $id"}
    }
    private fun advisory(id:UUID){jdbc.execute("SELECT pg_advisory_xact_lock(${java.nio.ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(id.toString().toByteArray(Charsets.UTF_8))).long})")}
    private fun uuid(node:JsonNode,field:String="id")=UUID.fromString(s(node,field))
    private fun s(node:JsonNode,field:String)=node[field].asText()
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
}
